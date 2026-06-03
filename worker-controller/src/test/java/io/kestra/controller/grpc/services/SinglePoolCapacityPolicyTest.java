package io.kestra.controller.grpc.services;

import java.util.List;

import io.kestra.core.worker.QueueSubscription;
import io.kestra.controller.grpc.services.WorkerStreamContext.PendingJob;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SinglePoolCapacityPolicyTest {

    @Test
    void shouldReserveSharedSlotsUpToMaxConcurrency() {
        SinglePoolCapacityPolicy policy = new SinglePoolCapacityPolicy(3);

        for (int i = 0; i < 3; i++) {
            assertThat(policy.tryReserve("any")).isEqualTo(PendingJob.SHARED);
        }
        assertThat(policy.tryReserve("any")).isNull();
        assertThat(policy.hasCapacity("any")).isFalse();
    }

    @Test
    void shouldReleaseSlotAndAllowNewReservation() {
        SinglePoolCapacityPolicy policy = new SinglePoolCapacityPolicy(2);

        assertThat(policy.tryReserve("q1")).isEqualTo(PendingJob.SHARED);
        assertThat(policy.tryReserve("q1")).isEqualTo(PendingJob.SHARED);
        assertThat(policy.tryReserve("q1")).isNull();

        policy.release(PendingJob.SHARED);
        assertThat(policy.hasCapacity("q1")).isTrue();
        assertThat(policy.tryReserve("q1")).isEqualTo(PendingJob.SHARED);
    }

    @Test
    void shouldReportZeroGuaranteedAllocations() {
        SinglePoolCapacityPolicy policy = new SinglePoolCapacityPolicy(5);

        assertThat(policy.allocated("any")).isEqualTo(0);
        assertThat(policy.used("any")).isEqualTo(0);
        assertThat(policy.sharedAllocated()).isEqualTo(5);
        assertThat(policy.sharedUsed()).isEqualTo(0);
    }

    @Test
    void shouldReportSharedUsageAfterReservations() {
        SinglePoolCapacityPolicy policy = new SinglePoolCapacityPolicy(4);

        policy.tryReserve("q1");
        policy.tryReserve("q2");

        // Single-pool ignores the queue id — every reservation feeds the shared pool.
        assertThat(policy.sharedUsed()).isEqualTo(2);
        assertThat(policy.used("q1")).isEqualTo(0);
        assertThat(policy.used("q2")).isEqualTo(0);
    }

    @Test
    void shouldIgnoreNullBucketOnRelease() {
        SinglePoolCapacityPolicy policy = new SinglePoolCapacityPolicy(2);
        policy.tryReserve("q1");

        policy.release(null);
        assertThat(policy.sharedUsed()).isEqualTo(1);
    }

    @Test
    void shouldClampReleaseAtZero() {
        SinglePoolCapacityPolicy policy = new SinglePoolCapacityPolicy(2);

        // Defensive: an extra release must not underflow into negative usage.
        policy.release(PendingJob.SHARED);
        assertThat(policy.sharedUsed()).isEqualTo(0);
    }

    @Test
    void shouldNotChangeBehaviorOnReplaceSubscriptions() {
        SinglePoolCapacityPolicy policy = new SinglePoolCapacityPolicy(2);
        policy.tryReserve("q1");
        assertThat(policy.sharedUsed()).isEqualTo(1);

        policy.replaceSubscriptions(List.of(new QueueSubscription("q2", 50)));

        // Single-pool ignores subscriptions; the in-flight slot stays reserved
        // and capacity math is unchanged.
        assertThat(policy.sharedUsed()).isEqualTo(1);
        assertThat(policy.sharedAllocated()).isEqualTo(2);
        assertThat(policy.allocated("q2")).isEqualTo(0);
    }
}
