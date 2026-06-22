package org.mosh4j.core.datagram;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FragmentCodecTest {

    @Test
    void singleFragmentRoundtrip() {
        byte[] payload = "the quick brown fox jumps over the lazy dog".getBytes();
        byte[] fragment = FragmentCodec.encodeSingle(1L, payload);
        byte[] decoded = new FragmentCodec().decode(fragment);
        assertArrayEquals(payload, decoded);
    }

    @Test
    void multiFragmentRoundtrip() {
        byte[] payload = new byte[10_000];
        new java.util.Random(42).nextBytes(payload); // incompressible -> spans fragments
        List<byte[]> fragments = FragmentCodec.encode(2L, payload, 1400);
        assertTrue(fragments.size() > 1, "payload should span multiple fragments");

        FragmentCodec decoder = new FragmentCodec();
        byte[] decoded = null;
        for (byte[] f : fragments) {
            decoded = decoder.decode(f);
        }
        assertArrayEquals(payload, decoded);
    }

    @Test
    void decompressionBombRejected() {
        // ~17 MiB of zeros compresses to a tiny fragment but would decompress past the
        // 16 MiB cap, so decode must refuse it rather than allocating it.
        byte[] huge = new byte[17 * 1024 * 1024];
        byte[] fragment = FragmentCodec.encodeSingle(3L, huge);
        assertTrue(fragment.length < huge.length, "compressed fragment should be far smaller");

        FragmentCodec decoder = new FragmentCodec();
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(fragment));
    }

    @Test
    void payloadAtLimitStillDecodes() {
        // A payload comfortably under the cap must round-trip normally.
        byte[] payload = new byte[1_000_000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i & 0xFF);
        }
        List<byte[]> fragments = FragmentCodec.encode(4L, payload, 1400);
        FragmentCodec decoder = new FragmentCodec();
        byte[] decoded = null;
        for (byte[] f : fragments) {
            decoded = decoder.decode(f);
        }
        assertArrayEquals(payload, decoded);
    }
}
