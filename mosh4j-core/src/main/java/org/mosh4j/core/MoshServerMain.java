package org.mosh4j.core;

import org.mosh4j.crypto.MoshKey;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Entry point to run mosh4j as a server for testing.
 * Prints "MOSH CONNECT &lt;port&gt; &lt;key&gt;" so a client can connect.
 * <p>
 * Usage: port and key can be set via env MOSH_PORT, MOSH_KEY, or default port 60001 and a random key.
 */
public final class MoshServerMain {

    private static final int DEFAULT_PORT = 60001;
    private static final int WIDTH = 80;
    private static final int HEIGHT = 24;

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        String portEnv = System.getenv("MOSH_PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            port = Integer.parseInt(portEnv.trim());
        }
        if (args.length > 0 && args[0] != null && !args[0].isEmpty()) {
            port = Integer.parseInt(args[0].trim());
        }

        String keyBase64 = System.getenv("MOSH_KEY");
        if (keyBase64 == null || keyBase64.trim().length() != 22) {
            keyBase64 = generateKeyBase64();
        } else {
            keyBase64 = keyBase64.trim();
        }

        MoshKey key = MoshKey.fromBase64(keyBase64);
        MoshServerSession session = new MoshServerSession(port, key, WIDTH, HEIGHT);

        System.err.println("MOSH CONNECT " + port + " " + keyBase64);
        System.err.flush();

        boolean firstContact = true;
        while (session.isRunning()) {
            boolean got = session.receiveOnce();
            if (!got) break;
            if (firstContact) {
                firstContact = false;
                byte[] banner = ("mosh4j test server (port " + port + ")\r\n").getBytes(StandardCharsets.UTF_8);
                session.feedHostOutput(banner);
            }
        }
        session.close();
    }

    private static String generateKeyBase64() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().withoutPadding().encodeToString(key).substring(0, 22);
    }
}
