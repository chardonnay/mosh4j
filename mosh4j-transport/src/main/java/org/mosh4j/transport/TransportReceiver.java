package org.mosh4j.transport;

import TransportBuffers.Transportinstruction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * SSP transport receiver: receives Instructions, applies diffs, maintains state window.
 */
public class TransportReceiver {

    private final Map<Long, byte[]> states = new HashMap<>();
    private long latestStateNum = 0;
    private byte[] latestState = null;
    private final BiFunction<byte[], byte[], byte[]> applyDiff;
    private final Consumer<byte[]> onNewState;

    /**
     * @param applyDiff  (baseState, diff) -> newState; baseState may be null for old_num 0
     * @param onNewState called when we have a new latest state
     */
    public TransportReceiver(
            BiFunction<byte[], byte[], byte[]> applyDiff,
            Consumer<byte[]> onNewState) {
        this.applyDiff = applyDiff;
        this.onNewState = onNewState;
        states.put(0L, new byte[0]);
    }

    /**
     * Process an incoming Instruction. Returns the ack_num we should send back (latest state we have).
     */
    public long receive(Transportinstruction.Instruction instruction) {
        long oldNum = instruction.hasOldNum() ? instruction.getOldNum() : 0;
        long newNum = instruction.hasNewNum() ? instruction.getNewNum() : 0;
        long throwawayNum = instruction.hasThrowawayNum() ? instruction.getThrowawayNum() : 0;

        byte[] base = states.get(oldNum);
        if (base == null) {
            return latestStateNum;
        }

        byte[] diff = instruction.hasDiff() ? instruction.getDiff().toByteArray() : new byte[0];
        byte[] newState = applyDiff.apply(base, diff);
        if (newState == null) {
            return latestStateNum;
        }

        states.put(newNum, newState);
        if (newNum > latestStateNum) {
            latestStateNum = newNum;
            latestState = newState;
            onNewState.accept(newState);
        }

        for (long i = 0; i < throwawayNum; i++) {
            states.remove(i);
        }
        return latestStateNum;
    }

    public long getLatestStateNum() {
        return latestStateNum;
    }

    public byte[] getLatestState() {
        return latestState;
    }
}
