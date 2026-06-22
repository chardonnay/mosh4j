package org.mosh4j.core;

import HostBuffers.Hostinput;
import TransportBuffers.Transportinstruction;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mosh4j.core.datagram.FragmentCodec;
import org.mosh4j.core.datagram.SspDatagramCodec;
import org.mosh4j.crypto.MoshKey;
import org.mosh4j.crypto.SspCipher;
import org.mosh4j.transport.TransportInstruction;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live UDP loopback integration tests for the Mosh4J session stack. These spin up
 * real {@link DatagramSocket}s on the loopback interface and exercise the full
 * encrypt &rarr; fragment &rarr; UDP &rarr; decrypt &rarr; reassemble &rarr; decode path,
 * including the anti-replay and freshness hardening.
 *
 * <p>Reusable harness: helpers craft well-formed server&rarr;client datagrams the
 * same way a real {@code mosh-server} would, so new behaviours can be added by
 * sending more datagrams and asserting on {@link MoshClientSession#pollHostBytes()}.
 */
class MoshLoopbackRoundtripTest {

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private static MoshKey randomKey() {
        byte[] k = new byte[16];
        new SecureRandom().nextBytes(k);
        return MoshKey.fromBytes(k);
    }

    /**
     * Drives the real {@link MoshClientSession} receive path with correctly-formed
     * server datagrams over a live UDP socket and verifies that:
     * <ul>
     *   <li>genuine host output is decrypted, reassembled, decoded and delivered;</li>
     *   <li>an exact datagram replay is dropped by the anti-replay window (M-1);</li>
     *   <li>a fresh datagram carrying an already-seen state is dropped by the
     *       transport freshness guard (M-1);</li>
     *   <li>a genuinely newer state is still delivered.</li>
     * </ul>
     */
    @Test
    @Timeout(30)
    void clientDecodesHostOutputAndDropsReplaysOverUdp() throws Exception {
        MoshKey key = randomKey();
        try (DatagramSocket server = new DatagramSocket(new InetSocketAddress(LOOPBACK, 0))) {
            server.setSoTimeout(5000);
            int serverPort = server.getLocalPort();

            MoshClientSession client = new MoshClientSession(
                    new InetSocketAddress(LOOPBACK, serverPort), key, 80, 24);
            try {
                // The client announces itself so we learn its ephemeral address.
                client.sendInitialWakeUp();
                DatagramPacket inbound = new DatagramPacket(new byte[2048], 2048);
                server.receive(inbound);
                SocketAddress clientAddr = inbound.getSocketAddress();

                SspDatagramCodec codec = new SspDatagramCodec(new SspCipher(key));

                // 1) Deliver real host output "hello" as state 0 -> 1.
                byte[] helloPkt = buildHostPacket(codec, 10L, 0, 1, "hello", 1L);
                sendRaw(server, clientAddr, helloPkt);
                assertEquals("hello", asString(pumpHostBytes(client, 5000)),
                        "host output should be decoded and delivered");

                // 2) Exact byte-for-byte replay -> dropped by the anti-replay window.
                sendRaw(server, clientAddr, helloPkt);
                drainReceive(client);
                assertNull(client.pollHostBytes(), "exact datagram replay must not be re-delivered");

                // 3) Fresh datagram sequence but a stale (already-seen) state number ->
                //    passes the replay window, dropped by the transport freshness guard.
                byte[] staleStatePkt = buildHostPacket(codec, 11L, 0, 1, "should-not-appear", 2L);
                sendRaw(server, clientAddr, staleStatePkt);
                drainReceive(client);
                assertNull(client.pollHostBytes(), "non-advancing state must not re-run the diff");

                // 4) A genuinely newer state (1 -> 2) is still delivered.
                byte[] worldPkt = buildHostPacket(codec, 12L, 1, 2, "world", 3L);
                sendRaw(server, clientAddr, worldPkt);
                assertEquals("world", asString(pumpHostBytes(client, 5000)),
                        "a newer state must still be delivered after replays were dropped");
            } finally {
                client.close();
            }
        }
    }

    /**
     * Confirms the two production session classes complete a bidirectional UDP
     * roundtrip over loopback: the client's datagram is decrypted by the server
     * (which then learns the roaming address), and the server's reply is received
     * by the client. Receiving a server datagram proves the shared-key crypto and
     * UDP path work in both directions.
     */
    @Test
    @Timeout(30)
    void productionClientAndServerCompleteUdpRoundtrip() throws Exception {
        MoshKey key = randomKey();

        MoshServerSession server = null;
        int port = -1;
        for (int attempt = 0; attempt < 3 && server == null; attempt++) {
            int candidate = freeUdpPort();
            try {
                server = new MoshServerSession(candidate, key, 80, 24);
                port = candidate;
            } catch (Exception e) {
                if (attempt == 2) throw e;
            }
        }

        MoshClientSession client = new MoshClientSession(
                new InetSocketAddress(LOOPBACK, port), key, 80, 24);
        // The bundled MoshServerSession sends framebuffer-state bytes as its diff, whereas the
        // client expects a HostBuffers.HostMessage (a real mosh-server's format). The client
        // therefore logs an expected parse WARNING and falls back; this test only asserts the
        // datagram-level roundtrip, so silence that expected noise.
        Logger clientLog = Logger.getLogger(MoshClientSession.class.getName());
        Level prevLevel = clientLog.getLevel();
        clientLog.setLevel(Level.SEVERE);
        try {
            // The server's receive() blocks until a datagram arrives, so run the single
            // initial receive on a helper thread and keep sending wake-ups until it returns.
            AtomicBoolean serverGotClient = new AtomicBoolean(false);
            final MoshServerSession serverRef = server;
            Thread serverReceive = new Thread(() -> serverGotClient.set(serverRef.receiveOnce()),
                    "server-initial-receive");
            serverReceive.start();

            long deadline = System.currentTimeMillis() + 10_000;
            while (serverReceive.isAlive() && System.currentTimeMillis() < deadline) {
                client.sendInitialWakeUp();
                Thread.sleep(50);
            }
            serverReceive.join(2000);
            assertTrue(serverGotClient.get(), "server should receive and decrypt the client's datagram");

            // The server now knows the client; its host output should reach the client.
            boolean clientGotServer = false;
            deadline = System.currentTimeMillis() + 10_000;
            while (!clientGotServer && System.currentTimeMillis() < deadline) {
                server.feedHostOutput("output\r\n".getBytes());
                if (client.receiveOnce()) {
                    clientGotServer = true;
                }
            }
            assertTrue(clientGotServer, "client should receive a server datagram over UDP");
        } finally {
            clientLog.setLevel(prevLevel);
            client.close();
            server.close();
        }
    }

    // ---- helpers ----

    /** Build a well-formed server&rarr;client datagram carrying host output, as a real mosh-server would. */
    private static byte[] buildHostPacket(SspDatagramCodec codec, long seq, long oldNum, long newNum,
                                          String text, long fragId) {
        Hostinput.HostBytes hb = Hostinput.HostBytes.newBuilder()
                .setHoststring(ByteString.copyFromUtf8(text))
                .build();
        Hostinput.Instruction instr = Hostinput.Instruction.newBuilder()
                .setExtension(Hostinput.hostbytes, hb)
                .build();
        Hostinput.HostMessage msg = Hostinput.HostMessage.newBuilder()
                .addInstruction(instr)
                .build();
        Transportinstruction.Instruction inst =
                TransportInstruction.create(oldNum, newNum, 0, 0, msg.toByteArray());
        byte[] fragment = FragmentCodec.encodeSingle(fragId, TransportInstruction.toBytes(inst));
        int ts = (int) (System.currentTimeMillis() & 0xFFFF);
        return codec.encode(true, seq, ts, 0, fragment);
    }

    private static void sendRaw(DatagramSocket sock, SocketAddress to, byte[] data) throws IOException {
        sock.send(new DatagramPacket(data, data.length, to));
    }

    /** Receive (and process) any datagrams already buffered on the client, then return. */
    private static void drainReceive(MoshClientSession client) {
        while (client.receiveOnce()) {
            // keep consuming until the socket times out (no more pending datagrams)
        }
    }

    /** Pump the client's receive loop until host bytes arrive or the budget elapses. */
    private static byte[] pumpHostBytes(MoshClientSession client, long budgetMs) {
        long end = System.currentTimeMillis() + budgetMs;
        byte[] b;
        while ((b = client.pollHostBytes()) == null && System.currentTimeMillis() < end) {
            client.receiveOnce();
        }
        return b;
    }

    private static String asString(byte[] b) {
        assertNotNull(b, "expected host bytes but none were delivered");
        return new String(b);
    }

    private static int freeUdpPort() throws IOException {
        try (DatagramSocket s = new DatagramSocket(new InetSocketAddress(LOOPBACK, 0))) {
            return s.getLocalPort();
        }
    }
}
