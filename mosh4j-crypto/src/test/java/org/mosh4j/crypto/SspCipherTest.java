package org.mosh4j.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;

import static org.junit.jupiter.api.Assertions.*;

class SspCipherTest {

    @Test
    void encryptDecrypt_roundtrip() throws AEADBadTagException {
        MoshKey key = MoshKey.fromBytes(new byte[16]);
        SspCipher cipher = new SspCipher(key);
        byte[] plain = "hello mosh".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] ct = cipher.encrypt(false, 0, plain);
        assertTrue(ct.length > plain.length);

        byte[] nonce = Nonce.create(false, 0);
        byte[] dec = cipher.decrypt(nonce, ct);
        assertArrayEquals(plain, dec);
    }

    @Test
    void decrypt_wrongNonce_fails() {
        MoshKey key = MoshKey.fromBytes(new byte[16]);
        SspCipher cipher = new SspCipher(key);
        byte[] plain = "test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] ct = cipher.encrypt(false, 0, plain);
        byte[] wrongNonce = Nonce.create(false, 1);
        assertThrows(AEADBadTagException.class, () -> cipher.decrypt(wrongNonce, ct));
    }

    @Test
    void decrypt_tamperedCiphertext_fails() {
        MoshKey key = MoshKey.fromBytes(new byte[16]);
        SspCipher cipher = new SspCipher(key);
        byte[] plain = "test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] ct = cipher.encrypt(false, 0, plain);
        if (ct.length > 0) ct[ct.length - 1] ^= 1;
        byte[] nonce = Nonce.create(false, 0);
        assertThrows(AEADBadTagException.class, () -> cipher.decrypt(nonce, ct));
    }

    @Test
    void differentSeq_differentCiphertext() {
        MoshKey key = MoshKey.fromBytes(new byte[16]);
        SspCipher cipher = new SspCipher(key);
        byte[] plain = "same".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] ct0 = cipher.encrypt(false, 0, plain);
        byte[] ct1 = cipher.encrypt(false, 1, plain);
        assertFalse(java.util.Arrays.equals(ct0, ct1));
    }
}
