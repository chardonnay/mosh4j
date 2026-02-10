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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mosh server session: accepts a client (roaming), receives user input, sends host output.
 */
public class MoshServerSession {

    private final DatagramChannel channel;
    private final SspDatagramCodec codec;
    private final Framebuffer framebuffer;
    private final TransportSender outputSender;
    private final TransportReceiver inputReceiver;
    private final AtomicReference<InetSocketAddress> clientAddress = new AtomicReference<>();
    private final AtomicLong sendSeq = new AtomicLong(0);
    private final RttEstimator rtt = new RttEstimator();
    private volatile boolean running = true;

    public MoshServerSession(int port, MoshKey key, int width, int height) throws Exception {
        DatagramSocket socket = new DatagramSocket(port);
        this.channel = new UdpDatagramChannel(socket);
        SspCipher cipher = new SspCipher(key);
        this.codec = new SspDatagramCodec(cipher);
        this.framebuffer = new SimpleFramebuffer(width, height);

        this.inputReceiver = new TransportReceiver(
                (base, diff) -> diff,
                state -> {});

        this.outputSender = new TransportSender(
                () -> ((SimpleFramebuffer) framebuffer).toStateBytes(),
                () -> ((SimpleFramebuffer) framebuffer).toStateBytes());
    }

    /**
     * Feed host output (terminal bytes from PTY) into the framebuffer and send an update to the client.
     */
    public void feedHostOutput(byte[] hostBytes) {
        if (hostBytes == null || hostBytes.length == 0) return;
        framebuffer.feedHostBytes(hostBytes);
        InetSocketAddress client = clientAddress.get();
        if (client == null) return;
        long seq = sendSeq.getAndIncrement();
        int ts = (int) (System.currentTimeMillis() & 0xFFFF);
        Transportinstruction.Instruction inst = outputSender.nextInstruction(outputSender.getAssumedReceiverState());
        if (inst == null) return;
        byte[] payload = TransportInstruction.toBytes(inst);
        byte[] packet = codec.encode(true, seq, ts, 0, payload);
        channel.send(client, packet);
    }

    /**
     * Receive one datagram. Updates client address (roaming) and processes user input.
     */
    public boolean receiveOnce() {
        DatagramChannel.ReceiveResult result = channel.receive();
        if (result == null || !running) return false;
        try {
            DatagramPayload payload = codec.decode(result.packet());
            if (!payload.isServerToClient()) {
                clientAddress.set(result.source());
                if (payload.getPayload().length > 0) {
                    Transportinstruction.Instruction inst = TransportInstruction.parse(payload.getPayload());
                    inputReceiver.receive(inst);
                    outputSender.setKnownReceiverState(inst.hasAckNum() ? inst.getAckNum() : 0);
                }
            }
        } catch (Exception e) {
            // ignore
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
