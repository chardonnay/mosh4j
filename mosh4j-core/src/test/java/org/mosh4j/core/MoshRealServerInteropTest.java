package org.mosh4j.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mosh4j.crypto.MoshKey;
import org.mosh4j.terminal.Cell;
import org.mosh4j.terminal.Framebuffer;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Wire-compatibility interop test against the <em>real</em> upstream C
 * {@code mosh-server} (mobile-shell/mosh). It launches an actual {@code mosh-server new}
 * session running a command that prints a known marker, connects the Mosh4J
 * {@link MoshClientSession} to it over UDP using the server-provided key, and asserts
 * that the marker shows up in the client's framebuffer — proving the Mosh4J client
 * decrypts (AES-128-OCB), reassembles, and decodes the genuine mosh wire format.
 *
 * <p>Self-skips (JUnit assumption) when {@code mosh-server} is not installed, so it is
 * safe in CI. Run locally with mosh installed to get end-to-end confirmation.
 */
@Tag("interop")
class MoshRealServerInteropTest {

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
    private static final String MARKER = "MOSH4J_ROUNDTRIP_OK";
    private static final Pattern CONNECT = Pattern.compile("MOSH CONNECT (\\d+) (\\S+)");
    private static final Pattern DETACHED_PID = Pattern.compile("pid = (\\d+)");

    @Test
    @Timeout(60)
    void mosh4jClientInteroperatesWithRealMoshServer() throws Exception {
        String moshServer = findMoshServer();
        assumeTrue(moshServer != null, "mosh-server not installed; skipping real-server interop test");

        Spawned server = null;
        MoshClientSession client = null;
        try {
            server = spawnMoshServer(moshServer);
            assertNotNull(server, "could not start mosh-server / parse its MOSH CONNECT line");

            MoshKey key = MoshKey.fromBase64(server.key);
            client = new MoshClientSession(new InetSocketAddress(LOOPBACK, server.port), key, 80, 24);

            // Prompt the server to start sending the framebuffer (it must hear from us first).
            client.sendInitialWakeUp();

            boolean found = false;
            String screen = "";
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline && !found) {
                boolean gotPacket = client.receiveOnce();
                screen = renderFramebuffer(client.getFramebuffer());
                if (screen.contains(MARKER)) {
                    found = true;
                } else if (!gotPacket) {
                    client.sendHeartbeat(); // nudge the server while idle
                }
            }

            assertTrue(found,
                    "Mosh4J client did not receive the marker from the real mosh-server.\n"
                            + "Server stdout:\n" + server.output + "\nClient framebuffer:\n" + screen);
        } finally {
            if (client != null) client.close();
            if (server != null) server.kill();
        }
    }

    // ---- helpers ----

    private record Spawned(int port, String key, Integer pid, String output) {
        void kill() {
            // mosh-server double-forks, so the printed pid is not necessarily the long-lived
            // server. Kill that pid AND anything bound to our unique port for reliable teardown.
            if (pid != null) {
                runQuietly("kill", String.valueOf(pid));
            }
            runQuietly("pkill", "-f", "mosh-server.*-p " + port);
        }

        private static void runQuietly(String... cmd) {
            try {
                new ProcessBuilder(cmd).start().waitFor();
            } catch (Exception ignored) {
                // best effort; mosh-server also self-terminates after its command exits / idle timeout
            }
        }
    }

    /** Launch {@code mosh-server new} on a free port with a marker-printing command; parse the handshake. */
    private static Spawned spawnMoshServer(String moshServer) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            int port = freeUdpPort();
            ProcessBuilder pb = new ProcessBuilder(
                    moshServer, "new",
                    "-i", "127.0.0.1",
                    "-p", String.valueOf(port),
                    "--",
                    "/bin/sh", "-c", "printf '" + MARKER + "\\n'; sleep 25");
            // mosh-server refuses to run without a UTF-8 locale.
            pb.environment().put("LANG", "en_US.UTF-8");
            pb.environment().put("LC_ALL", "en_US.UTF-8");
            pb.redirectErrorStream(true);

            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            p.waitFor();

            Matcher cm = CONNECT.matcher(out);
            if (cm.find()) {
                int actualPort = Integer.parseInt(cm.group(1));
                String key = cm.group(2);
                Matcher pm = DETACHED_PID.matcher(out);
                Integer pid = pm.find() ? Integer.parseInt(pm.group(1)) : null;
                return new Spawned(actualPort, key, pid, out.toString());
            }
            // Port likely busy or transient failure; try another port.
        }
        return null;
    }

    private static String renderFramebuffer(Framebuffer fb) {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < fb.getHeight(); row++) {
            for (int col = 0; col < fb.getWidth(); col++) {
                Cell cell = fb.getCell(row, col);
                if (cell != null) {
                    for (int cp : cell.getCodePoints()) {
                        sb.appendCodePoint(cp);
                    }
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static int freeUdpPort() throws Exception {
        try (java.net.DatagramSocket s = new java.net.DatagramSocket(new InetSocketAddress(LOOPBACK, 0))) {
            return s.getLocalPort();
        }
    }

    /** Locate the mosh-server binary on PATH or in common install locations; null if absent. */
    private static String findMoshServer() {
        List<String> candidates = new ArrayList<>();
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                candidates.add(dir + File.separator + "mosh-server");
            }
        }
        candidates.add("/opt/homebrew/bin/mosh-server");
        candidates.add("/usr/local/bin/mosh-server");
        candidates.add("/usr/bin/mosh-server");
        for (String c : candidates) {
            File f = new File(c);
            if (f.isFile() && f.canExecute()) {
                return c;
            }
        }
        return null;
    }
}
