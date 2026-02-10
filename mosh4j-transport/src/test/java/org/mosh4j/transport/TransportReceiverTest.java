package org.mosh4j.transport;

import TransportBuffers.Transportinstruction;
import org.junit.jupiter.api.Test;

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
}
