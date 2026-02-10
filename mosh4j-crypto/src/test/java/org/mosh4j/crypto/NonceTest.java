package org.mosh4j.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NonceTest {

    @Test
    void create_and_getSequence_roundtrip() {
        byte[] n0 = Nonce.create(false, 0);
        assertEquals(8, n0.length);
        assertEquals(0, Nonce.getSequence(n0));
        assertFalse(Nonce.directionServerToClient(n0));

        byte[] n1 = Nonce.create(true, 1);
        assertEquals(1, Nonce.getSequence(n1));
        assertTrue(Nonce.directionServerToClient(n1));

        long big = 0x7FFF_FFFF_FFFF_FFFFL;
        byte[] nBig = Nonce.create(false, big);
        assertEquals(big, Nonce.getSequence(nBig));
    }

    @Test
    void create_rejectsNegativeSeq() {
        assertThrows(IllegalArgumentException.class, () -> Nonce.create(false, -1));
    }

    @Test
    void create_rejects64BitSeq() {
        assertThrows(IllegalArgumentException.class, () -> Nonce.create(false, 0x8000_0000_0000_0000L));
    }
}
