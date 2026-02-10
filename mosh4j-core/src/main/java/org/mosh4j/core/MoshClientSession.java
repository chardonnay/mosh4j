package org.mosh4j.core;

import org.mosh4j.core.datagram.*;
import org.mosh4j.crypto.MoshKey;
import org.mosh4j.crypto.SspCipher;
import org.mosh4j.terminal.Framebuffer;
import org.mosh4j.terminal.SimpleFramebuffer;
import org.mosh4j.transport.TransportInstruction;
import org.mosh4j.transport.TransportReceiver;
import org.mosh4j.transport.TransportSender;

import TransportBuffers.Transportinstruction;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mosh client session: connects to a mosh-server, sends user input, receives and displays host output.
 */
public class MoshClientSession {

    private final InetSocketAddress serverAddress;
    private final DatagramChannel channel;
    private final SspDatagramCodec codec;
    private final Framebuffer framebuffer;
    private final TransportSender inputSender;
    private final TransportReceiver outputReceiver;
    private final AtomicLong sendSeq = new AtomicLong(0);
    private volatile boolean running = true;

    public MoshClientSession(InetSocketAddress serverAddress, MoshKey key, int width, int height) throws Exception {
        this.serverAddress = serverAddress;
        DatagramSocket socket = new DatagramSocket();
        this.channel = new UdpDatagramChannel(socket);
        SspCipher cipher = new SspCipher(key);
        this.codec = new SspDatagramCodec(cipher);
        this.framebuffer = new SimpleFramebuffer(width, height);

        this.outputReceiver = new TransportReceiver(
                (base, diff) -> {
                    SimpleFramebuffer buf = (SimpleFramebuffer) framebuffer;
                    if (diff != null && diff.length >= 2 && diff[0] == 'W' && new String(diff, 0, Math.min(20, diff.length), StandardCharsets.UTF_8).contains("H")) {
                        buf.fromStateBytes(diff);
                    } else {
                        if (base != null && base.length > 0) buf.fromStateBytes(base);
                        if (diff != null && diff.length > 0) buf.feedHostBytes(diff);
                    }
                    return buf.toStateBytes();
                },
                state -> {});

        this.inputSender = new TransportSender(
                () -> new byte[0],
                () -> new byte[0]);
    }

    /**
     * Send user input (keystrokes) to the server. Encodes as a transport instruction and sends one datagram.
     */
    public void sendUserInput(byte[] keys) {
        if (keys == null || keys.length == 0) return;
        long seq = sendSeq.getAndIncrement();
        int ts = (int) (System.currentTimeMillis() & 0xFFFF);
        int tsReply = 0;
        Transportinstruction.Instruction inst = TransportInstruction.create(0, seq, 0, 0, keys);
        byte[] payload = TransportInstruction.toBytes(inst);
        byte[] packet = codec.encode(false, seq, ts, tsReply, payload);
        channel.send(serverAddress, packet);
    }

    /**
     * Receive one datagram and process it (update framebuffer from server output). Call in a loop or from a thread.
     */
    public boolean receiveOnce() {
        DatagramChannel.ReceiveResult result = channel.receive();
        if (result == null || !running) return false;
        try {
            DatagramPayload payload = codec.decode(result.packet());
            if (payload.isServerToClient()) {
                if (payload.getPayload().length > 0) {
                    Transportinstruction.Instruction inst = TransportInstruction.parse(payload.getPayload());
                    outputReceiver.receive(inst);
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
}
