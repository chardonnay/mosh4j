package org.mosh4j.transport;

import TransportBuffers.Transportinstruction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * SSP transport sender: produces Instructions to bring the receiver from its state to current state.
 * Keeps a rolling window of sent states; uses known receiver state and RTO for assumed receiver state.
 */
public class TransportSender {

    private static final int MAX_PENDING_STATES = 32;

    private long nextStateNum = 1;
    private long knownReceiverState = 0;
    private final List<Long> sentStateNums = new ArrayList<>();
    private final Supplier<byte[]> currentStateSupplier;
    private final Supplier<byte[]> diffSupplier;
    private int protocolVersion = 0;

    /**
     * @param currentStateSupplier returns current state bytes (for diff and identity)
     * @param diffSupplier          returns diff from knownReceiverState to current state (or minimal diff)
     */
    public TransportSender(Supplier<byte[]> currentStateSupplier, Supplier<byte[]> diffSupplier) {
        this.currentStateSupplier = currentStateSupplier;
        this.diffSupplier = diffSupplier;
    }

    public void setProtocolVersion(int version) {
        this.protocolVersion = version;
    }

    /**
     * Update known receiver state from an incoming ack (receiver's ack_num).
     */
    public void setKnownReceiverState(long ackNum) {
        if (ackNum > knownReceiverState) {
            knownReceiverState = ackNum;
            pruneSentStates(ackNum);
        }
    }

    private void pruneSentStates(long before) {
        sentStateNums.removeIf(n -> n <= before);
    }

    /**
     * Assumed receiver state: most recent state we assume they have (e.g. last sent before RTO).
     * Caller can use RTO to decide; default we use known receiver state.
     */
    public long getAssumedReceiverState() {
        return knownReceiverState;
    }

    public long getKnownReceiverState() {
        return knownReceiverState;
    }

    /**
     * Produce the next instruction to send (from assumed receiver state to current state).
     * Returns null if no update needed (current state already sent and acked).
     */
    public Transportinstruction.Instruction nextInstruction(long assumedReceiverState) {
        byte[] current = currentStateSupplier.get();
        if (current == null) return null;

        long targetNum = nextStateNum++;
        byte[] diff = diffSupplier.get();
        if (diff == null) diff = new byte[0];

        if (sentStateNums.size() >= MAX_PENDING_STATES) {
            assumedReceiverState = knownReceiverState;
        }

        Transportinstruction.Instruction inst = TransportInstruction.create(
                assumedReceiverState,
                targetNum,
                knownReceiverState,
                knownReceiverState,
                diff.length > 0 ? diff : null);
        if (protocolVersion != 0) {
            inst = inst.toBuilder().setProtocolVersion(protocolVersion).build();
        }
        sentStateNums.add(targetNum);
        return inst;
    }

    /**
     * Build instruction for a trial/ack only (empty diff), e.g. for heartbeat.
     */
    public Transportinstruction.Instruction createTrialInstruction() {
        return TransportInstruction.createAckOnly(knownReceiverState, knownReceiverState);
    }
}
