package org.mosh4j.core.datagram;

import org.mosh4j.crypto.Nonce;
import org.mosh4j.crypto.SspCipher;

import javax.crypto.AEADBadTagException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Encode/decode SSP datagrams: 8-byte nonce (clear) + OCB ciphertext (timestamp, timestamp_reply, payload).
 */
public final class SspDatagramCodec {

    private static final int TIMESTAMP_BYTES = 2;
    private static final int HEADER_PLAINTEXT_BYTES = 4; // timestamp + timestamp_reply

    private final SspCipher cipher;

    public SspDatagramCodec(SspCipher cipher) {
        this.cipher = cipher;
    }

    /**
     * Encode a datagram for sending: nonce (8) + encrypt(timestamp, timestamp_reply, payload).
     */
    public byte[] encode(boolean serverToClient, long seq, int timestamp, int timestampReply, byte[] payload) {
        byte[] nonce = Nonce.create(serverToClient, seq);
        int plen = payload != null ? payload.length : 0;
        byte[] plain = new byte[HEADER_PLAINTEXT_BYTES + plen];
        ByteBuffer buf = ByteBuffer.wrap(plain).order(ByteOrder.BIG_ENDIAN);
        buf.putShort((short) (timestamp & 0xFFFF));
        buf.putShort((short) (timestampReply & 0xFFFF));
        if (plen > 0) buf.put(payload);
        byte[] ciphertext = cipher.encrypt(serverToClient, seq, plain);
        byte[] out = new byte[Nonce.length() + ciphertext.length];
        System.arraycopy(nonce, 0, out, 0, nonce.length);
        System.arraycopy(ciphertext, 0, out, nonce.length, ciphertext.length);
        return out;
    }

    /**
     * Decode a received datagram: parse nonce, decrypt, return payload and metadata.
     */
    public DatagramPayload decode(byte[] packet) throws AEADBadTagException {
        if (packet == null || packet.length <= Nonce.length()) {
            throw new IllegalArgumentException("Packet too short");
        }
        byte[] nonce = new byte[Nonce.length()];
        System.arraycopy(packet, 0, nonce, 0, nonce.length);
        byte[] ciphertext = new byte[packet.length - nonce.length];
        System.arraycopy(packet, nonce.length, ciphertext, 0, ciphertext.length);
        byte[] plain = cipher.decrypt(nonce, ciphertext);
        if (plain.length < HEADER_PLAINTEXT_BYTES) {
            throw new IllegalArgumentException("Decrypted payload too short");
        }
        ByteBuffer buf = ByteBuffer.wrap(plain).order(ByteOrder.BIG_ENDIAN);
        int timestamp = buf.getShort() & 0xFFFF;
        int timestampReply = buf.getShort() & 0xFFFF;
        byte[] payload = new byte[plain.length - HEADER_PLAINTEXT_BYTES];
        if (payload.length > 0) buf.get(payload);
        boolean serverToClient = Nonce.directionServerToClient(nonce);
        long seq = Nonce.getSequence(nonce);
        return new DatagramPayload(seq, timestamp, timestampReply, payload, serverToClient);
    }
}
