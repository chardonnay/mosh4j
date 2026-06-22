package org.mosh4j.transport;

import TransportBuffers.Transportinstruction;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TransportReceiverTest {

    @Test
    void receive_appliesDiffAndUpdatesState() {
        AtomicReference<byte[]> lastState = new AtomicReference<>();
        TransportReceiver recv = new TransportReceiver(
                (base, diff) -> {
                    String b = base == null || base.length == 0 ? "" : new String(base);
                    String d = new String(diff);
                    return (b + d).getBytes();
                },
                lastState::set);

        Transportinstruction.Instruction i1 = TransportInstruction.create(0, 1, 0, 0, "hi".getBytes());
        long ack = recv.receive(i1);
        assertEquals(1, ack);
        assertArrayEquals("hi".getBytes(), recv.getLatestState());
        assertArrayEquals("hi".getBytes(), lastState.get());

        Transportinstruction.Instruction i2 = TransportInstruction.create(1, 2, 1, 0, "!".getBytes());
        ack = recv.receive(i2);
        assertEquals(2, ack);
        assertArrayEquals("hi!".getBytes(), recv.getLatestState());
    }

    @Test
    void replayedOrStaleDiffDoesNotReRunSideEffect() {
        AtomicInteger applyCalls = new AtomicInteger();
        AtomicInteger newStateCalls = new AtomicInteger();
        TransportReceiver recv = new TransportReceiver(
                (base, diff) -> {
                    applyCalls.incrementAndGet();
                    String b = base == null || base.length == 0 ? "" : new String(base);
                    return (b + new String(diff)).getBytes();
                },
                state -> newStateCalls.incrementAndGet());

        Transportinstruction.Instruction i1 = TransportInstruction.create(0, 1, 0, 0, "hi".getBytes());
        recv.receive(i1);
        assertEquals(1, applyCalls.get());
        assertEquals(1, newStateCalls.get());

        // Replaying the same state must NOT re-run the side-effecting diff.
        recv.receive(i1);
        assertEquals(1, applyCalls.get(), "stale/replayed diff must not re-run applyDiff");
        assertEquals(1, newStateCalls.get());

        // A genuinely newer state is still applied.
        Transportinstruction.Instruction i2 = TransportInstruction.create(1, 2, 1, 0, "!".getBytes());
        recv.receive(i2);
        assertEquals(2, applyCalls.get());
        assertEquals(2, newStateCalls.get());
        assertArrayEquals("hi!".getBytes(), recv.getLatestState());

        // An old state replayed after advancing is also ignored.
        recv.receive(i1);
        assertEquals(2, applyCalls.get());
        assertEquals(2, newStateCalls.get());
    }
}
