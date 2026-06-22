package org.mosh4j.core.datagram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReplayWindowTest {

    @Test
    void firstSequenceAccepted() {
        ReplayWindow w = new ReplayWindow();
        assertTrue(w.accept(0));
    }

    @Test
    void duplicateRejected() {
        ReplayWindow w = new ReplayWindow();
        assertTrue(w.accept(5));
        assertFalse(w.accept(5), "exact replay of the highest sequence must be rejected");
    }

    @Test
    void monotonicSequencesAccepted() {
        ReplayWindow w = new ReplayWindow();
        for (long s = 0; s < 100; s++) {
            assertTrue(w.accept(s), "fresh increasing sequence " + s + " must be accepted");
        }
        // replay each
        for (long s = 0; s < 100; s++) {
            assertFalse(w.accept(s), "replayed sequence " + s + " must be rejected");
        }
    }

    @Test
    void outOfOrderWithinWindowAccepted() {
        ReplayWindow w = new ReplayWindow();
        assertTrue(w.accept(10));
        // a legitimately reordered earlier datagram still inside the window
        assertTrue(w.accept(8));
        assertTrue(w.accept(9));
        // but replays of them are now rejected
        assertFalse(w.accept(8));
        assertFalse(w.accept(9));
        assertFalse(w.accept(10));
    }

    @Test
    void staleBeyondWindowRejected() {
        ReplayWindow w = new ReplayWindow(64);
        assertTrue(w.accept(1000));
        // 1000 - 64 = 936 is exactly the window edge -> too old
        assertFalse(w.accept(936));
        assertFalse(w.accept(0));
        // just inside the window is still accepted
        assertTrue(w.accept(1000 - 63));
    }

    @Test
    void largeForwardJumpClearsWindowButAcceptsNew() {
        ReplayWindow w = new ReplayWindow(64);
        assertTrue(w.accept(5));
        assertTrue(w.accept(10_000));
        assertFalse(w.accept(5), "old sequence after a large jump is outside the window");
        assertTrue(w.accept(10_001));
    }

    @Test
    void negativeRejected() {
        ReplayWindow w = new ReplayWindow();
        assertFalse(w.accept(-1));
    }
}
