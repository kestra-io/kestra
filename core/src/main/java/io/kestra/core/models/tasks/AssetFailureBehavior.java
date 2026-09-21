package io.kestra.core.models.tasks;

import io.kestra.core.models.flows.State;

public enum AssetFailureBehavior {
    IGNORE,
    FAIL,
    WARN;

    /**
     * Apply this behavior to <code>current</code>:
     * - never escalates a state that already terminated in error (FAILED/KILLED/CANCELLED)
     * - FAIL -> always FAILED
     * - WARN -> WARNING, only if <code>current</code> is SUCCESS
     * - IGNORE -> unchanged
     */
    public State.Type apply(State.Type current) {
        if (current.isTerminatedInError()) {
            return current;
        }
        if (this == FAIL) {
            return State.Type.FAILED;
        }
        if (this == WARN && current == State.Type.SUCCESS) {
            return State.Type.WARNING;
        }
        return current;
    }
}
