package io.kestra.core.worker;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kestra.core.utils.Enums;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * A worker's subscription to a Worker Queue, identified by the queue's
 * {@code workerQueueId} (or {@link WorkerQueues#DEFAULT_ID} for the global default queue).
 *
 * <p>{@code reservedPercent} is the minimum percentage of a worker's slots guaranteed
 * to this Worker Queue, in {@code [1, 100]}, or {@link #NO_RESERVATION} ({@code -1}) to
 * consume only unreserved (shared) capacity.
 *
 * <p>The sum of reserved percentages across a worker's subscriptions must be
 * {@code <= 100}; the remainder forms a shared pool any subscribed Worker Queue may use.
 *
 * <p>{@code mode} controls how this subscription's reserved slots interact with other
 * subscriptions on the same worker — see {@link Mode}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueueSubscription(
    @NotBlank String workerQueueId,
    @Min(-1) @Max(100) int reservedPercent,
    @Nullable Mode mode) {

    /** Sentinel value meaning "no reservation for this Worker Queue". */
    public static final int NO_RESERVATION = -1;

    /**
     * Reservation interaction mode — lender-only semantics.
     *
     * <p>{@link #STRICT} (default): the subscription's reserved slots are exclusive —
     * no other subscription on the same worker may use them, even when idle.
     *
     * <p>{@link #ELASTIC}: the subscription's idle reserved slots may be borrowed by
     * any other subscription on the same worker. The reserved percentage becomes a
     * soft floor — borrowed slots are not preempted, so a busy lender may temporarily
     * fall short of its full floor until borrowed jobs complete naturally. Whether
     * <em>this</em> subscription borrows from elsewhere is independent of its mode:
     * borrowing-from-others is universal and not gated by the borrower's mode.
     */
    public enum Mode {
        STRICT,
        ELASTIC,
        UNKNOWN;

        @JsonCreator
        public static Mode fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, Mode.class, UNKNOWN);
        }
    }

    /** Default mode applied when {@code mode} is null on the wire (back-compat). */
    public static final Mode DEFAULT_MODE = Mode.STRICT;

    public QueueSubscription {
        if (workerQueueId == null || workerQueueId.isBlank()) {
            throw new IllegalArgumentException("workerQueueId must not be null or blank");
        }
        if (reservedPercent != NO_RESERVATION && (reservedPercent < 1 || reservedPercent > 100)) {
            throw new IllegalArgumentException(
                "reservedPercent must be -1 (no reservation) or in [1, 100], got " + reservedPercent);
        }
        // null (legacy payload) or UNKNOWN — degrade safely to STRICT.
        if (mode == null || mode == Mode.UNKNOWN) {
            mode = DEFAULT_MODE;
        }
    }

    /** Two-arg overload preserved for back-compat (defaults {@code mode} to STRICT). */
    public QueueSubscription(String workerQueueId, int reservedPercent) {
        this(workerQueueId, reservedPercent, DEFAULT_MODE);
    }

    /** Subscription to the global default queue with no reservation. */
    public static final QueueSubscription DEFAULT = new QueueSubscription(WorkerQueues.DEFAULT_ID, NO_RESERVATION, DEFAULT_MODE);

    /**
     * Returns the dispatch-side routing key: empty string for the default queue
     * ({@link WorkerQueues#DEFAULT_ID}), or the id verbatim for named queues.
     */
    public String normalizedWorkerQueueId() {
        return WorkerQueues.isDefault(workerQueueId) ? "" : workerQueueId;
    }

    public boolean hasReservation() {
        return reservedPercent > 0;
    }

    public boolean isElastic() {
        return mode == Mode.ELASTIC;
    }
}
