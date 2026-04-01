package io.kestra.core.lock;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.server.ServerInstance;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwRunnable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@KestraTest
@Slf4j
@Execution(ExecutionMode.SAME_THREAD) // This test cannot be run concurrently because of lockThenDeleteByOwner() that release all locks
public class LockServiceTest {
    @Inject
    private LockService lockService;

    @Test
    void doInLock() throws LockException {
        lockService.doInLock("category", "doInLock", Duration.ofSeconds(1), () -> log.info("I'm here"));
    }

    @Test
    void isLocked() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);
        Thread.ofVirtual().start(
            throwRunnable(
                () ->
                {
                    lockService.doInLock("category", "isLocked", Duration.ofSeconds(1), throwRunnable(() ->
                    {
                        startLatch.countDown();
                        log.info("Start a long transaction");
                        Thread.sleep(100);
                        log.info("End a long transaction");
                    }));
                    endLatch.countDown();
                }
            )
        );
        // make sure the first transaction begins
        assertTrue(startLatch.await(1, TimeUnit.SECONDS));

        assertTrue(lockService.isLocked("category", "isLocked"));

        // make sure the lock is released
        assertTrue(endLatch.await(1, TimeUnit.SECONDS));
    }

    @Test
    void doInLockShouldWaitForSameLock() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);
        Thread.ofVirtual().start(
            throwRunnable(
                () ->
                {
                    lockService.doInLock("category", "doInLockShouldWaitForSameLock", Duration.ofSeconds(1), throwRunnable(() ->
                    {
                        startLatch.countDown();
                        log.info("Start a long transaction");
                        Thread.sleep(100);
                        log.info("End a long transaction");
                    }));
                    endLatch.countDown();
                }
            )
        );
        // make sure the first transaction begins
        assertTrue(startLatch.await(1, TimeUnit.SECONDS));

        lockService.doInLock("category", "doInLockShouldWaitForSameLock", Duration.ofSeconds(1), () -> log.info("I'm here"));

        // make sure the lock is released
        assertTrue(endLatch.await(1, TimeUnit.SECONDS));
    }

    @Test
    void doInLockShouldNotWaitForDifferentLock() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);
        Thread.ofVirtual().start(
            throwRunnable(
                () ->
                {
                    lockService.doInLock("category", "doInLockShouldNotWaitForDifferentLock", Duration.ofSeconds(1), throwRunnable(() ->
                    {
                        startLatch.countDown();
                        log.info("Start a long transaction");
                        Thread.sleep(100);
                        log.info("End a long transaction");
                    }));
                    endLatch.countDown();
                }
            )
        );
        // make sure the first transaction begins
        assertTrue(startLatch.await(1, TimeUnit.SECONDS));

        lockService.doInLock("other", "doInLockShouldNotWaitForDifferentLock", Duration.ofSeconds(1), () -> log.info("I'm here"));

        // make sure the lock is released
        assertTrue(endLatch.await(1, TimeUnit.SECONDS));
    }

    @Test
    void doInLockShouldFailForSameLock() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);
        Thread.ofVirtual().start(
            throwRunnable(
                () ->
                {
                    lockService.doInLock("category", "doInLockShouldFailForSameLock", Duration.ofSeconds(10), throwRunnable(() ->
                    {
                        startLatch.countDown();
                        log.info("Start a long transaction");
                        Thread.sleep(500);
                        log.info("End a long transaction");
                    }));
                    endLatch.countDown();
                }
            )
        );
        // make sure the first transaction begins
        assertTrue(startLatch.await(1, TimeUnit.SECONDS));

        assertThrows(
            LockException.class, () -> lockService.doInLock("category", "doInLockShouldFailForSameLock", Duration.ofMillis(100), () -> log.info("I'm here"))
        );

        // make sure the lock is released
        assertTrue(endLatch.await(1, TimeUnit.SECONDS));
    }

    @Test
    void doInLockShouldNotFailForDifferentLock() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);
        Thread.ofVirtual().start(
            throwRunnable(
                () ->
                {
                    lockService.doInLock("category", "doInLockShouldNotFailForDifferentLock", Duration.ofSeconds(10), throwRunnable(() ->
                    {
                        startLatch.countDown();
                        log.info("Start a long transaction");
                        Thread.sleep(500);
                        log.info("End a long transaction");
                    }));
                    endLatch.countDown();
                }
            )
        );
        // make sure the first transaction begins
        assertTrue(startLatch.await(1, TimeUnit.SECONDS));

        lockService.doInLock("other", "doInLockShouldNotFailForDifferentLock", Duration.ofMillis(100), () -> log.info("I'm here"));

        // make sure the lock is released
        assertTrue(endLatch.await(1, TimeUnit.SECONDS));
    }

    @Test
    void lockThenDeleteByOwner() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        Thread.ofVirtual().start(
            throwRunnable(
                () -> lockService.doInLock("category", "lockThenDeleteByOwner", Duration.ofSeconds(10), throwRunnable(() ->
                {
                    startLatch.countDown();
                    log.info("Start a long transaction");
                    Thread.sleep(500);
                    log.info("End a long transaction");
                }))
            )
        );
        // make sure the first transaction begins
        assertTrue(startLatch.await(1, TimeUnit.SECONDS));

        List<Lock> released = lockService.releaseAllLocks(ServerInstance.INSTANCE_ID);
        assertThat(released).hasSize(1);
        assertThat(released.getFirst().getOwner()).isEqualTo(ServerInstance.INSTANCE_ID);
        assertThat(released.getFirst().getCategory()).isEqualTo("category");
        assertThat(released.getFirst().getId()).isEqualTo("lockThenDeleteByOwner");
    }

    @Test
    void tryLock() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);
        Thread.ofVirtual().start(
            throwRunnable(
                () ->
                {
                    lockService.tryLock("category", "tryLock", throwRunnable(() ->
                    {
                        startLatch.countDown();
                        log.info("Start a long transaction");
                        Thread.sleep(500);
                        log.info("End a long transaction");
                    }));
                    endLatch.countDown();
                }
            )
        );
        // make sure the first transaction begins
        assertTrue(startLatch.await(1, TimeUnit.SECONDS));

        AtomicBoolean executed = new AtomicBoolean(false);
        Thread.ofVirtual().start(
            throwRunnable(
                () -> lockService.tryLock("category", "tryLock", throwRunnable(() ->
                {
                    executed.set(true);
                }))
            )
        );

        // wait for the first transaction to end
        assertThat(endLatch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(executed.get()).isFalse();
    }
}