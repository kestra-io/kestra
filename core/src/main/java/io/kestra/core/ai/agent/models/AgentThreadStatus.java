package io.kestra.core.ai.agent.models;

public enum AgentThreadStatus {
    /** No turn in flight; the thread can accept a new one. */
    IDLE,
    /** A turn is currently being processed. */
    RUNNING,
    /** A turn is suspended, waiting for the user to confirm a proposed action. */
    AWAITING_CONFIRMATION
}
