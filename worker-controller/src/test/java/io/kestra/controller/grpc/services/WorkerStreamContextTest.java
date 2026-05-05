package io.kestra.controller.grpc.services;

import java.util.List;

import io.kestra.core.worker.QueueSubscription;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerStreamContextTest {

    @SuppressWarnings("unchecked")
    private StreamObserver<Object> obs() {
        return Mockito.mock(StreamObserver.class);
    }

    @Test
    void guaranteedBucketConsumedFirst() {
        // Given: 10-slot worker, group A reserves 30% (guaranteed=3), group B has no reservation
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(new QueueSubscription("A", 30), new QueueSubscription("B", QueueSubscription.NO_RESERVATION)),
            10, obs()
        );
        ctx.setPermits(10);

        // When: place 3 A tasks (= guaranteed_A capacity)
        for (int i = 0; i < 3; i++) {
            String bucket = ctx.tryReserveBucket("A");
            assertThat(bucket).isEqualTo("A");
        }

        // Then: guaranteed_A is exhausted, shared (10-3=7) is still untouched
        assertThat(ctx.guaranteedFree("A")).isEqualTo(0);
        assertThat(ctx.sharedFree()).isEqualTo(7);
    }

    @Test
    void sharedBucketUsedWhenGuaranteedFull() {
        // Given: 10-slot, A=30%, B=20%. shared=5, guaranteed_A=3.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(new QueueSubscription("A", 30), new QueueSubscription("B", 20)),
            10, obs()
        );
        ctx.setPermits(10);

        // When: fill guaranteed_A with 3 A tasks, then place another A
        for (int i = 0; i < 3; i++) {
            assertThat(ctx.tryReserveBucket("A")).isEqualTo("A");
        }
        String bucket = ctx.tryReserveBucket("A");

        // Then: 4th A spills into the shared bucket
        assertThat(bucket).isEqualTo(WorkerStreamContext.PendingJob.SHARED);
        assertThat(ctx.sharedFree()).isEqualTo(4);
    }

    @Test
    void returnsNullWhenGroupFullyFull() {
        // Given: 10-slot, A=30%, B=20%. shared=5, guaranteed_A=3, guaranteed_B=2.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(new QueueSubscription("A", 30), new QueueSubscription("B", 20)),
            10, obs()
        );
        ctx.setPermits(10);

        // When: fill guaranteed_A with 3 A, then spill 5 more A into shared (A total=8)
        for (int i = 0; i < 3; i++) ctx.tryReserveBucket("A");
        for (int i = 0; i < 5; i++) ctx.tryReserveBucket("A");

        // Then: no more capacity for A (guaranteed_A full + shared full)
        assertThat(ctx.tryReserveBucket("A")).isNull();
        // But B can still use its guaranteed bucket
        assertThat(ctx.tryReserveBucket("B")).isEqualTo("B");
    }

    @Test
    void releaseBucketFreesSlot() {
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(new QueueSubscription("A", 30)),
            10, obs()
        );
        ctx.setPermits(10);

        // First reservation goes to A's guaranteed bucket (consumed before shared)
        String bucket = ctx.tryReserveBucket("A");
        assertThat(bucket).isEqualTo("A");
        assertThat(ctx.guaranteedFree("A")).isEqualTo(2);
        ctx.releaseBucket(bucket);
        assertThat(ctx.guaranteedFree("A")).isEqualTo(3);
    }

    @Test
    void subscriptionWithoutReservationRunsInSharedOnly() {
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(new QueueSubscription("A", 50), new QueueSubscription("B", QueueSubscription.NO_RESERVATION)),
            10, obs()
        );
        ctx.setPermits(10);

        // Shared = 5, guaranteed_A=5, guaranteed_B=0
        // Fill shared with B tasks
        for (int i = 0; i < 5; i++) {
            assertThat(ctx.tryReserveBucket("B")).isEqualTo(WorkerStreamContext.PendingJob.SHARED);
        }
        // B has no guaranteed bucket → next B reservation must fail
        assertThat(ctx.tryReserveBucket("B")).isNull();
        // A still has its guaranteed_A
        assertThat(ctx.tryReserveBucket("A")).isEqualTo("A");
    }

    @Test
    void roundingCollapsesToZeroOnTinyWorker() {
        // Given: 5-slot worker, reservedPercent=10 → floor(5 * 10/100) = 0
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(new QueueSubscription("A", 10)),
            5, obs()
        );
        ctx.setPermits(5);
        assertThat(ctx.guaranteedFree("A")).isEqualTo(0);
        assertThat(ctx.sharedFree()).isEqualTo(5);
    }

    // ---------- ELASTIC borrowing tests ----------

    @Test
    void shouldBorrowFromIdleElasticLenderWhenOwnAndSharedAreFull() {
        // Given: 10 slots, A=ELASTIC 30%, B=ELASTIC 30%. shared=4.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(
                new QueueSubscription("A", 30, QueueSubscription.Mode.ELASTIC),
                new QueueSubscription("B", 30, QueueSubscription.Mode.ELASTIC)
            ),
            10, obs()
        );
        ctx.setPermits(10);

        // When: fill B's own guaranteed (3) and shared (4) — 7 B tasks placed.
        for (int i = 0; i < 3; i++) {
            assertThat(ctx.tryReserveBucket("B")).isEqualTo("B");
        }
        for (int i = 0; i < 4; i++) {
            assertThat(ctx.tryReserveBucket("B")).isEqualTo(WorkerStreamContext.PendingJob.SHARED);
        }

        // Then: A is idle, so B's 8th task borrows from A's bucket.
        String bucket = ctx.tryReserveBucket("B");
        assertThat(bucket).isEqualTo("A");
        assertThat(ctx.guaranteedFree("A")).isEqualTo(2);

        // Releasing the borrowed slot returns it to A's counter.
        ctx.releaseBucket(bucket);
        assertThat(ctx.guaranteedFree("A")).isEqualTo(3);
    }

    @Test
    void shouldAllowBorrowWhenBorrowerIsStrictAndLenderIsElastic() {
        // Given: B is STRICT (default), A is ELASTIC. Borrower's mode is irrelevant
        // — only the lender opts in by being ELASTIC.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(
                new QueueSubscription("A", 30, QueueSubscription.Mode.ELASTIC),
                new QueueSubscription("B", 30) // STRICT by default
            ),
            10, obs()
        );
        ctx.setPermits(10);

        // When: fill B's own (3) + shared (4) = 7 B tasks.
        for (int i = 0; i < 3; i++) ctx.tryReserveBucket("B");
        for (int i = 0; i < 4; i++) ctx.tryReserveBucket("B");

        // Then: 8th B borrows from A's idle elastic reservation.
        assertThat(ctx.tryReserveBucket("B")).isEqualTo("A");
        assertThat(ctx.guaranteedFree("A")).isEqualTo(2);
    }

    @Test
    void shouldAllowDefaultModeBorrowerToBorrowFromElasticLender() {
        // Given: borrower constructed via the 2-arg overload (no mode → default STRICT);
        // lender is ELASTIC. The default STRICT borrower must still be able to borrow.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(
                new QueueSubscription("A", 30, QueueSubscription.Mode.ELASTIC),
                new QueueSubscription("B", 30)
            ),
            10, obs()
        );
        ctx.setPermits(10);

        // When: saturate B's own + shared.
        for (int i = 0; i < 3; i++) ctx.tryReserveBucket("B");
        for (int i = 0; i < 4; i++) ctx.tryReserveBucket("B");

        // Then: borrow succeeds even though B was constructed without an explicit mode.
        assertThat(ctx.tryReserveBucket("B")).isEqualTo("A");
    }

    @Test
    void shouldSkipStrictLendersWhenBorrowing() {
        // Given: B is ELASTIC, A is STRICT.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(
                new QueueSubscription("A", 30), // STRICT
                new QueueSubscription("B", 30, QueueSubscription.Mode.ELASTIC)
            ),
            10, obs()
        );
        ctx.setPermits(10);

        // When: fill B's own (3) + shared (4) = 7 B tasks.
        for (int i = 0; i < 3; i++) ctx.tryReserveBucket("B");
        for (int i = 0; i < 4; i++) ctx.tryReserveBucket("B");

        // Then: A is STRICT — B cannot borrow.
        assertThat(ctx.tryReserveBucket("B")).isNull();
        assertThat(ctx.guaranteedFree("A")).isEqualTo(3);
    }

    @Test
    void shouldReportCapacityWhenBorrowableLenderHasFreeSlots() {
        // Given: B's own + shared full, but A elastic has free reserved slots.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(
                new QueueSubscription("A", 30, QueueSubscription.Mode.ELASTIC),
                new QueueSubscription("B", 30, QueueSubscription.Mode.ELASTIC)
            ),
            10, obs()
        );
        ctx.setPermits(10);
        for (int i = 0; i < 3; i++) ctx.tryReserveBucket("B");
        for (int i = 0; i < 4; i++) ctx.tryReserveBucket("B");

        // Then: B should report capacity (via borrow), not be paused.
        assertThat(ctx.hasCapacityForQueue("B")).isTrue();
    }

    @Test
    void shouldReportNoCapacityWhenNoElasticLenderAvailable() {
        // Given: B's own + shared full, and the only other subscription (A) is STRICT.
        // No ELASTIC lender exists on this worker — borrowing has nowhere to go,
        // regardless of B's own mode.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(
                new QueueSubscription("A", 30), // STRICT — refuses to lend
                new QueueSubscription("B", 30, QueueSubscription.Mode.ELASTIC)
            ),
            10, obs()
        );
        ctx.setPermits(10);
        for (int i = 0; i < 3; i++) ctx.tryReserveBucket("B");
        for (int i = 0; i < 4; i++) ctx.tryReserveBucket("B");

        assertThat(ctx.hasCapacityForQueue("B")).isFalse();
    }

    @Test
    void shouldPreserveOwnFloorWhenLendingElasticSlots() {
        // Given: A and B both elastic, A=30, B=30, shared=4.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(
                new QueueSubscription("A", 30, QueueSubscription.Mode.ELASTIC),
                new QueueSubscription("B", 30, QueueSubscription.Mode.ELASTIC)
            ),
            10, obs()
        );
        ctx.setPermits(10);

        // When: B borrows one slot from A.
        for (int i = 0; i < 3; i++) ctx.tryReserveBucket("B");
        for (int i = 0; i < 4; i++) ctx.tryReserveBucket("B");
        assertThat(ctx.tryReserveBucket("B")).isEqualTo("A");

        // Then: A still has 2 of its own reserved slots.
        assertThat(ctx.tryReserveBucket("A")).isEqualTo("A");
        assertThat(ctx.tryReserveBucket("A")).isEqualTo("A");
        // 3rd A request fails — bucket fully used (2 own + 1 borrowed), no other lender.
        assertThat(ctx.tryReserveBucket("A")).isNull();
    }

    // ---------- Bucket-release-on-completion tests ----------

    @Test
    void shouldHoldCapAcrossDispatchedButNotCompletedJobs() {
        // Given: maxConcurrency=32, 25% STRICT for "test" → guaranteed=8, shared=24.
        // This mirrors the user-reported config that was over-filling at 32/32.
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(
                new QueueSubscription("default", QueueSubscription.NO_RESERVATION),
                new QueueSubscription("test", 25)
            ),
            32, obs()
        );
        ctx.setPermits(32);

        // When: dispatch 24 default jobs (own=0, all SHARED).
        for (int i = 0; i < 24; i++) {
            String bucket = ctx.tryReserveBucket("default");
            assertThat(bucket).isEqualTo(WorkerStreamContext.PendingJob.SHARED);
            ctx.trackInFlight("job-" + i, null, bucket);
        }

        // Then: shared cap holds across dispatched-but-not-completed jobs — a
        // 25th default reservation must fail because the 24 in-flight jobs are
        // still occupying their slots from a capacity POV.
        assertThat(ctx.tryReserveBucket("default")).isNull();
        // And the 8 test slots stay locked under STRICT.
        assertThat(ctx.sharedFree()).isEqualTo(0);
        assertThat(ctx.guaranteedFree("test")).isEqualTo(8);

        // Completing one frees one slot — the cap releases incrementally.
        ctx.completeJob("job-0");
        assertThat(ctx.sharedFree()).isEqualTo(1);
        assertThat(ctx.tryReserveBucket("default")).isEqualTo(WorkerStreamContext.PendingJob.SHARED);
    }

    @Test
    void shouldReleaseBucketOnCompletion() {
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(new QueueSubscription("A", 30)),
            10, obs()
        );
        ctx.setPermits(10);

        String bucket = ctx.tryReserveBucket("A");
        ctx.trackInFlight("job-1", null, bucket);
        assertThat(ctx.guaranteedFree("A")).isEqualTo(2);

        // completeJob both removes in-flight and releases the bucket.
        ctx.completeJob("job-1");
        assertThat(ctx.guaranteedFree("A")).isEqualTo(3);
        assertThat(ctx.getInFlightCount()).isEqualTo(0);
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
            List.of(
                new QueueSubscription("default", QueueSubscription.NO_RESERVATION),
                new QueueSubscription("test", 25)
            ),
            32, obs()
        );
        ctx.setPermits(32);

        // Dispatch a mix: 5 default (SHARED) + 3 test (own guaranteed).
        for (int i = 0; i < 5; i++) {
            String bucket = ctx.tryReserveBucket("default");
            ctx.trackInFlight("d-" + i, null, bucket);
        }
        for (int i = 0; i < 3; i++) {
            String bucket = ctx.tryReserveBucket("test");
            ctx.trackInFlight("t-" + i, null, bucket);
        }
        assertThat(ctx.sharedFree()).isEqualTo(19);
        assertThat(ctx.guaranteedFree("test")).isEqualTo(5);

        // On stream close, every executing bucket is released.
        ctx.releaseAllInFlightBuckets();
        assertThat(ctx.sharedFree()).isEqualTo(24);
        assertThat(ctx.guaranteedFree("test")).isEqualTo(8);
        assertThat(ctx.getInFlightCount()).isEqualTo(0);
    }

    @Test
    void shouldSkipLendersWithoutReservationWhenBorrowing() {
        // Given: B elastic with reservation, A elastic but NO_RESERVATION (no slots to lend).
        WorkerStreamContext<Object> ctx = new WorkerStreamContext<>(
            "w1", "pool1",
            List.of(
                new QueueSubscription("A", QueueSubscription.NO_RESERVATION, QueueSubscription.Mode.ELASTIC),
                new QueueSubscription("B", 30, QueueSubscription.Mode.ELASTIC)
            ),
            10, obs()
        );
        ctx.setPermits(10);
        // shared = 7 (10 - 3). Fill B's own (3) + shared (7) = 10 tasks.
        for (int i = 0; i < 3; i++) ctx.tryReserveBucket("B");
        for (int i = 0; i < 7; i++) ctx.tryReserveBucket("B");

        // No lender has reserved capacity to lend.
        assertThat(ctx.tryReserveBucket("B")).isNull();
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
