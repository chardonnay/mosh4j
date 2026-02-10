package org.mosh4j.transport;

import TransportBuffers.Transportinstruction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransportInstructionTest {

    @Test
    void createAndParse_roundtrip() throws Exception {
        Transportinstruction.Instruction inst = TransportInstruction.create(0, 1, 0, 0, "hello".getBytes());
        byte[] bytes = TransportInstruction.toBytes(inst);
        Transportinstruction.Instruction parsed = TransportInstruction.parse(bytes);
        assertEquals(0, parsed.getOldNum());
        assertEquals(1, parsed.getNewNum());
        assertTrue(parsed.hasDiff());
        assertEquals("hello", new String(parsed.getDiff().toByteArray()));
    }

    @Test
    void createAckOnly() {
        Transportinstruction.Instruction ack = TransportInstruction.createAckOnly(5, 3);
        assertEquals(5, ack.getAckNum());
        assertEquals(3, ack.getThrowawayNum());
        assertFalse(ack.hasDiff());
    }
}
