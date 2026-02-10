package org.mosh4j.transport;

import TransportBuffers.Transportinstruction;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Wrapper for SSP transport Instruction (TransportBuffers.Instruction): parse and serialize.
 */
public final class TransportInstruction {

    private static final int MAX_FRAGMENT_SIZE = 1400;

    /**
     * Maximum size of a single instruction payload before fragmentation is needed (bytes).
     */
    public static int getMaxFragmentSize() {
        return MAX_FRAGMENT_SIZE;
    }

    /**
     * Parse an Instruction from bytes.
     */
    public static Transportinstruction.Instruction parse(byte[] bytes) throws IOException {
        return Transportinstruction.Instruction.parseFrom(bytes);
    }

    /**
     * Parse an Instruction from stream.
     */
    public static Transportinstruction.Instruction parseFrom(InputStream in) throws IOException {
        return Transportinstruction.Instruction.parseFrom(in);
    }

    /**
     * Serialize an Instruction to bytes.
     */
    public static byte[] toBytes(Transportinstruction.Instruction instruction) {
        Objects.requireNonNull(instruction, "instruction");
        return instruction.toByteArray();
    }

    /**
     * Build an instruction (idempotent update from old_num to new_num with diff).
     */
    public static Transportinstruction.Instruction create(
            long oldNum,
            long newNum,
            long ackNum,
            long throwawayNum,
            byte[] diff) {
        Transportinstruction.Instruction.Builder b = Transportinstruction.Instruction.newBuilder();
        b.setOldNum(oldNum);
        b.setNewNum(newNum);
        b.setAckNum(ackNum);
        b.setThrowawayNum(throwawayNum);
        if (diff != null && diff.length > 0) {
            b.setDiff(com.google.protobuf.ByteString.copyFrom(diff));
        }
        return b.build();
    }

    /**
     * Build a trial/ack-only instruction (empty diff).
     */
    public static Transportinstruction.Instruction createAckOnly(long ackNum, long throwawayNum) {
        return create(0, 0, ackNum, throwawayNum, null);
    }
}
