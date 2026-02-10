package org.mosh4j.crypto;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.OCBBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.crypto.AEADBadTagException;

/**
 * AES-128-OCB encrypt/decrypt for Mosh SSP datagrams.
 * Each message uses a unique nonce (direction + 63-bit sequence number).
 */
public final class SspCipher {

    private static final int TAG_BITS = 128;

    private final OCBBlockCipher encryptCipher;
    private final OCBBlockCipher decryptCipher;
    private final KeyParameter keyParam;

    public SspCipher(MoshKey key) {
        byte[] keyBytes = key.getKeyBytes();
        this.keyParam = new KeyParameter(keyBytes);
        this.encryptCipher = new OCBBlockCipher(new AESEngine(), new AESEngine());
        this.decryptCipher = new OCBBlockCipher(new AESEngine(), new AESEngine());
    }

    /**
     * Encrypt plaintext for the given direction and sequence number.
     *
     * @param serverToClient direction (true = server→client, false = client→server)
     * @param seq            63-bit sequence number
     * @param plaintext      data to encrypt (not modified)
     * @return ciphertext including authentication tag (new array)
     */
    public byte[] encrypt(boolean serverToClient, long seq, byte[] plaintext) {
        byte[] nonce = Nonce.create(serverToClient, seq);
        AEADParameters params = new AEADParameters(keyParam, TAG_BITS, nonce);
        encryptCipher.init(true, params);
        int outLen = encryptCipher.getOutputSize(plaintext.length);
        byte[] out = new byte[outLen];
        int len = encryptCipher.processBytes(plaintext, 0, plaintext.length, out, 0);
        try {
            len += encryptCipher.doFinal(out, len);
        } catch (org.bouncycastle.crypto.InvalidCipherTextException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
        return len == out.length ? out : java.util.Arrays.copyOf(out, len);
    }

    /**
     * Decrypt ciphertext (including tag). The nonce is taken from the first 8 bytes of the
     * received datagram (Mosh sends nonce in clear, then ciphertext+tag).
     *
     * @param nonceBytes first 8 bytes of the datagram (direction + seq)
     * @param ciphertext remaining bytes (encrypted payload + 16-byte tag)
     * @return decrypted plaintext
     * @throws AEADBadTagException if authentication fails
     */
    public byte[] decrypt(byte[] nonceBytes, byte[] ciphertext) throws AEADBadTagException {
        if (nonceBytes == null || nonceBytes.length < Nonce.length()) {
            throw new IllegalArgumentException("Nonce must be at least " + Nonce.length() + " bytes");
        }
        byte[] nonce = nonceBytes.length == Nonce.length() ? nonceBytes : java.util.Arrays.copyOf(nonceBytes, Nonce.length());
        AEADParameters params = new AEADParameters(keyParam, TAG_BITS, nonce);
        decryptCipher.init(false, params);
        int outLen = decryptCipher.getOutputSize(ciphertext.length);
        byte[] out = new byte[outLen];
        int len = decryptCipher.processBytes(ciphertext, 0, ciphertext.length, out, 0);
        try {
            len += decryptCipher.doFinal(out, len);
        } catch (org.bouncycastle.crypto.InvalidCipherTextException e) {
            throw new AEADBadTagException("OCB authentication failed: " + e.getMessage());
        }
        return len == out.length ? out : java.util.Arrays.copyOf(out, len);
    }
}
