package io.kestra.controller.grpc.services;

import java.util.List;

import io.kestra.core.worker.QueueSubscription;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests stream-context-level concerns: permits, in-flight tracking, completion,
 * and the convenience constructor that installs the {@link SinglePoolCapacityPolicy}.
 *
 * <p>Tests that exercise richer per-queue bucket math live alongside the
 * implementing policy.
 */
class WorkerStreamContextTest {

    @SuppressWarnings("unchecked")
    private StreamObserver<Object> obs() {
        return Mockito.mock(StreamObserver.class);
    }

    @Test
    void shouldUseSharedPoolForSingleNoReservationSubscription() {
        // Given: a default-shape worker — one subscription, no reservation.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(new QueueSubscription("default", QueueSubscription.NO_RESERVATION)),
            5, obs()
        );
        ctx.setPermits(5);

        // When: dispatch 5 jobs.
        for (int i = 0; i < 5; i++) {
            String bucket = ctx.tryReserveBucket("default");
            assertThat(bucket).isEqualTo(WorkerStreamContext.PendingJob.SHARED);
        }

        // Then: pool is full — single-pool policy reports no capacity, no
        // per-queue guaranteed math is involved.
        assertThat(ctx.tryReserveBucket("default")).isNull();
        assertThat(ctx.guaranteedCapacity("default")).isEqualTo(0);
        assertThat(ctx.sharedFree()).isEqualTo(0);
    }

    @Test
    void shouldReleaseSlotOnReleaseBucket() {
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(QueueSubscription.DEFAULT),
            3, obs()
        );
        ctx.setPermits(3);

        String bucket = ctx.tryReserveBucket("default");
        assertThat(ctx.sharedFree()).isEqualTo(2);

        ctx.releaseBucket(bucket);
        assertThat(ctx.sharedFree()).isEqualTo(3);
    }

    @Test
    void shouldHoldCapAcrossDispatchedButNotCompletedJobs() {
        // Given: 5-slot worker, single shared pool.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(QueueSubscription.DEFAULT),
            5, obs()
        );
        ctx.setPermits(5);

        // When: dispatch 5 jobs and track them in-flight (none completed).
        for (int i = 0; i < 5; i++) {
            String bucket = ctx.tryReserveBucket("default");
            ctx.trackInFlight("job-" + i, null, bucket);
        }

        // Then: cap holds — a 6th reservation must fail until a completion arrives.
        assertThat(ctx.tryReserveBucket("default")).isNull();
        assertThat(ctx.sharedFree()).isEqualTo(0);

        ctx.completeJob("job-0");
        assertThat(ctx.sharedFree()).isEqualTo(1);
        assertThat(ctx.tryReserveBucket("default")).isEqualTo(WorkerStreamContext.PendingJob.SHARED);
    }

    @Test
    void shouldReturnNullFromCompleteJobForUnknownJobId() {
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(new QueueSubscription("default", QueueSubscription.NO_RESERVATION)),
            10, obs()
        );
        // Unknown ids must be a no-op — supports the controller-restart scenario where
        // results for jobs dispatched on a prior stream arrive on a new controller.
        assertThat(ctx.completeJob("never-dispatched")).isNull();
        assertThat(ctx.sharedFree()).isEqualTo(10);
    }

    @Test
    void shouldReleaseAllInFlightBucketsOnStreamClose() {
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(QueueSubscription.DEFAULT),
            8, obs()
        );
        ctx.setPermits(8);

        // Dispatch 5 jobs and track them.
        for (int i = 0; i < 5; i++) {
            String bucket = ctx.tryReserveBucket("default");
            ctx.trackInFlight("d-" + i, null, bucket);
        }
        assertThat(ctx.sharedFree()).isEqualTo(3);

        // On stream close, every executing bucket is released.
        ctx.releaseAllInFlightBuckets();
        assertThat(ctx.sharedFree()).isEqualTo(8);
        assertThat(ctx.getInFlightCount()).isEqualTo(0);
    }

    @Test
    void shouldTrackPermitsIndependentlyFromCapacityPolicy() {
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(QueueSubscription.DEFAULT),
            10, obs()
        );

        ctx.setPermits(3);
        assertThat(ctx.getAvailablePermits()).isEqualTo(3);

        assertThat(ctx.tryConsumePermit()).isTrue();
        assertThat(ctx.getAvailablePermits()).isEqualTo(2);

        ctx.addPermits(5);
        assertThat(ctx.getAvailablePermits()).isEqualTo(7);
    }

    @Test
    void shouldRejectMoreThanAvailablePermits() {
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(QueueSubscription.DEFAULT),
            10, obs()
        );
        ctx.setPermits(2);

        assertThat(ctx.tryConsumePermit()).isTrue();
        assertThat(ctx.tryConsumePermit()).isTrue();
        // No more permits.
        assertThat(ctx.tryConsumePermit()).isFalse();
    }

    @Test
    void shouldReplaceSubscriptionsViaCapacityPolicy() {
        // Given: a context with a default subscription.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(QueueSubscription.DEFAULT),
            5, obs()
        );

        // When: swap to a new subscription set.
        ctx.replaceQueueSubscriptions(List.of(new QueueSubscription("alt", QueueSubscription.NO_RESERVATION)));

        // Then: the context reports the new subscription.
        assertThat(ctx.subscribedWorkerQueueIds()).containsExactly("alt");
    }

    @Test
    void shouldPreserveEmptySubscriptionsOnReplace() {
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(QueueSubscription.DEFAULT),
            5, obs()
        );

        ctx.replaceQueueSubscriptions(List.of());

        assertThat(ctx.subscribedWorkerQueueIds()).isEmpty();
    }

    @Test
    void shouldPreserveEmptySubscriptionsAtConstruction() {
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(),
            5, obs()
        );

        assertThat(ctx.subscribedWorkerQueueIds()).isEmpty();
    }
}
