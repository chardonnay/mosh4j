package org.mosh4j.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoshKeyTest {

    @Test
    void fromBase64_decodesTo16Bytes() {
        String base64 = "AAAAAAAAAAAAAAAAAAAAAA"; // 22 chars, no padding (Mosh format)
        MoshKey key = MoshKey.fromBase64(base64);
        byte[] bytes = key.getKeyBytes();
        assertEquals(16, bytes.length);
    }

    @Test
    void fromBase64_rejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> MoshKey.fromBase64("short"));
        assertThrows(IllegalArgumentException.class, () -> MoshKey.fromBase64("AAAAAAAAAAAAAAAAAAAAAAA"));
    }

    @Test
    void fromBytes_accepts16Bytes() {
        byte[] bytes = new byte[16];
        bytes[0] = 1;
        MoshKey key = MoshKey.fromBytes(bytes);
        byte[] got = key.getKeyBytes();
        assertEquals(16, got.length);
        assertEquals(1, got[0]);
        assertNotSame(bytes, got);
    }

    @Test
    void fromBytes_rejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> MoshKey.fromBytes(new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> MoshKey.fromBytes(new byte[17]));
    }
}
