package org.mosh4j.core;

import org.mosh4j.core.datagram.*;
import org.mosh4j.crypto.MoshKey;
import org.mosh4j.crypto.SspCipher;
import org.mosh4j.terminal.Framebuffer;
import org.mosh4j.terminal.SimpleFramebuffer;
import org.mosh4j.transport.TransportInstruction;
import org.mosh4j.transport.TransportReceiver;

import TransportBuffers.Transportinstruction;
import ClientBuffers.Userinput;
import HostBuffers.Hostinput;

import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mosh client session: connects to a mosh-server, sends user input, receives
 * and displays host output. Uses the native C++ mosh wire format:
 * <ul>
 *   <li>12-byte OCB nonce (4 zero prefix + 8 wire bytes)</li>
 *   <li>Fragment layer with zlib compression</li>
 *   <li>ClientBuffers.UserMessage for sending keystrokes</li>
 *   <li>HostBuffers.HostMessage for receiving terminal output</li>
 *   <li>protocol_version=2, random chaff bytes</li>
 * </ul>
 */
public class MoshClientSession {
    private static final Logger LOG = Logger.getLogger(MoshClientSession.class.getName());
    private static final int DEFAULT_UDP_RECEIVE_TIMEOUT_MS = 250;

    private final InetSocketAddress serverAddress;
    private final DatagramChannel channel;
    private final SspDatagramCodec codec;
    private final Framebuffer framebuffer;
    private final TransportReceiver outputReceiver;
    private final FragmentCodec fragmentDecoder;
    private final AtomicLong sendSeq = new AtomicLong(0);
    private final AtomicLong instructionId = new AtomicLong(0);
    private volatile boolean running = true;
    private volatile int lastTimestampReceived = 0;
    private volatile long lastReceivedServerSeq = 0;
    private volatile long lastAckedClientSeq = 0;

    private final ExtensionRegistry hostExtensionRegistry;

    public MoshClientSession(InetSocketAddress serverAddress, MoshKey key, int width, int height) throws Exception {
        this.serverAddress = serverAddress;
        DatagramSocket socket = new DatagramSocket();
        try {
            UdpDatagramChannel udpChannel = new UdpDatagramChannel(socket);
            udpChannel.setReceiveTimeoutMillis(DEFAULT_UDP_RECEIVE_TIMEOUT_MS);
            this.channel = udpChannel;
            SspCipher cipher = new SspCipher(key);
            this.codec = new SspDatagramCodec(cipher);
            this.framebuffer = new SimpleFramebuffer(width, height);
            this.fragmentDecoder = new FragmentCodec();

            hostExtensionRegistry = ExtensionRegistry.newInstance();
            Hostinput.registerAllExtensions(hostExtensionRegistry);

            this.outputReceiver = new TransportReceiver(
                    (base, diff) -> applyHostDiff(diff),
                    state -> {});
        } catch (Exception e) {
            socket.close();
            throw e;
        }
    }

    /**
     * Apply a diff received from the server. The diff contains a serialized
     * HostBuffers.HostMessage protobuf with HostBytes, ResizeMessage, EchoAck.
     */
    private byte[] applyHostDiff(byte[] diff) {
        if (diff == null || diff.length == 0) {
            return ((SimpleFramebuffer) framebuffer).toStateBytes();
        }
        try {
            Hostinput.HostMessage hostMsg = Hostinput.HostMessage.parseFrom(diff, hostExtensionRegistry);
            SimpleFramebuffer buf = (SimpleFramebuffer) framebuffer;
            for (Hostinput.Instruction instr : hostMsg.getInstructionList()) {
                if (instr.hasExtension(Hostinput.hostbytes)) {
                    Hostinput.HostBytes hb = instr.getExtension(Hostinput.hostbytes);
                    if (hb.hasHoststring()) {
                        buf.feedHostBytes(hb.getHoststring().toByteArray());
                    }
                }
                if (instr.hasExtension(Hostinput.resize)) {
                    Hostinput.ResizeMessage rm = instr.getExtension(Hostinput.resize);
                    if (rm.hasWidth() && rm.hasHeight()) {
                        buf.resize(rm.getWidth(), rm.getHeight());
                    }
                }
            }
            return buf.toStateBytes();
        } catch (InvalidProtocolBufferException e) {
            LOG.log(Level.WARNING, "Failed to parse HostMessage protobuf", e);
            if (framebuffer instanceof SimpleFramebuffer buf) {
                return buf.toStateBytes();
            }
            return new byte[0];
        }
    }

    /**
     * Send one harmless wake-up packet so servers that wait for first client input
     * start emitting framebuffer updates immediately.
     */
    public void sendInitialWakeUp() {
        sendUserInput(new byte[]{0});
    }

    /**
     * Send user input (keystrokes) to the server. Wraps in ClientBuffers.UserMessage,
     * then in a TransportBuffers.Instruction, fragments with zlib, and sends.
     */
    public void sendUserInput(byte[] keys) {
        if (keys == null || keys.length == 0) return;

        Userinput.Keystroke keystroke = Userinput.Keystroke.newBuilder()
                .setKeys(ByteString.copyFrom(keys))
                .build();
        Userinput.Instruction clientInstr = Userinput.Instruction.newBuilder()
                .setExtension(Userinput.keystroke, keystroke)
                .build();
        Userinput.UserMessage userMsg = Userinput.UserMessage.newBuilder()
                .addInstruction(clientInstr)
                .build();
        sendWrappedUserMessage(userMsg.toByteArray());
    }

    /**
     * Send a resize notification to the server.
     */
    public void sendResize(int width, int height) {
        Userinput.ResizeMessage resizeMsg = Userinput.ResizeMessage.newBuilder()
                .setWidth(width)
                .setHeight(height)
                .build();
        Userinput.Instruction clientInstr = Userinput.Instruction.newBuilder()
                .setExtension(Userinput.resize, resizeMsg)
                .build();
        Userinput.UserMessage userMsg = Userinput.UserMessage.newBuilder()
                .addInstruction(clientInstr)
                .build();
        sendWrappedUserMessage(userMsg.toByteArray());
    }

    private void sendWrappedUserMessage(byte[] userMsgBytes) {
        long seq = sendSeq.getAndIncrement();
        int ts = currentTimestamp();
        int tsReply = lastTimestampReceived;

        Transportinstruction.Instruction inst = TransportInstruction.create(
                lastAckedClientSeq, seq, lastReceivedServerSeq, lastReceivedServerSeq, userMsgBytes);
        byte[] protobufBytes = TransportInstruction.toBytes(inst);

        long fragId = instructionId.getAndIncrement();
        byte[] fragmentPayload = FragmentCodec.encodeSingle(fragId, protobufBytes);

        byte[] packet = codec.encode(false, seq, ts, tsReply, fragmentPayload);
        channel.send(serverAddress, packet);
    }

    /**
     * Receive one datagram, decrypt, reassemble fragments, decode protobuf,
     * and update the framebuffer. Call in a loop or from a thread.
     */
    public boolean receiveOnce() {
        DatagramChannel.ReceiveResult result = channel.receive();
        if (result == null || !running) return false;
        try {
            DatagramPayload payload = codec.decode(result.packet());
            if (payload.isServerToClient()) {
                lastTimestampReceived = payload.getTimestamp();
                byte[] fragmentData = payload.getPayload();
                if (fragmentData != null && fragmentData.length > 0) {
                    byte[] protobufBytes = fragmentDecoder.decode(fragmentData);
                    if (protobufBytes != null) {
                        Transportinstruction.Instruction inst = TransportInstruction.parse(protobufBytes);
                        long ack = outputReceiver.receive(inst);
                        lastReceivedServerSeq = ack;
                        if (inst.hasAckNum()) {
                            lastAckedClientSeq = inst.getAckNum();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore auth failures / bad packets
        }
        return true;
    }

    public Framebuffer getFramebuffer() {
        return framebuffer;
    }

    public void close() {
        running = false;
        channel.close();
    }

    public boolean isRunning() {
        return running;
    }

    private static int currentTimestamp() {
        return (int) (System.currentTimeMillis() & 0xFFFF);
    }
}
