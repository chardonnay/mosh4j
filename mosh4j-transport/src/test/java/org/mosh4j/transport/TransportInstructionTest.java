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
        assertEquals(0, ack.getOldNum());
        assertEquals(0, ack.getNewNum());
        assertEquals(5, ack.getAckNum());
        assertEquals(3, ack.getThrowawayNum());
        assertFalse(ack.hasDiff());
    }

    @Test
    void createAckOnly_withSenderState() {
        Transportinstruction.Instruction ack = TransportInstruction.createAckOnly(7, 7, 5, 3);
        assertEquals(7, ack.getOldNum());
        assertEquals(7, ack.getNewNum());
        assertEquals(5, ack.getAckNum());
        assertEquals(3, ack.getThrowawayNum());
        assertFalse(ack.hasDiff());
    }

    @Test
    void protocolVersionValidation() {
        // create() always stamps the current protocol version.
        Transportinstruction.Instruction valid = TransportInstruction.create(0, 1, 0, 0, "x".getBytes());
        assertTrue(TransportInstruction.isProtocolVersionValid(valid));

        // Missing protocol_version (default 0) must be rejected.
        Transportinstruction.Instruction missing = Transportinstruction.Instruction.newBuilder()
                .setOldNum(0).setNewNum(1).build();
        assertFalse(TransportInstruction.isProtocolVersionValid(missing));

        // Wrong protocol_version must be rejected.
        Transportinstruction.Instruction wrong = Transportinstruction.Instruction.newBuilder()
                .setProtocolVersion(1).setOldNum(0).setNewNum(1).build();
        assertFalse(TransportInstruction.isProtocolVersionValid(wrong));
    }

    @Test
    void parseRejectsOversizedInput() {
        byte[] tooBig = new byte[16 * 1024 * 1024 + 1];
        assertThrows(java.io.IOException.class, () -> TransportInstruction.parse(tooBig));
    }
}
