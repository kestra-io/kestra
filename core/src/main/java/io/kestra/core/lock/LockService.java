package io.kestra.core.lock;

import io.kestra.core.repositories.LockRepositoryInterface;
import io.kestra.core.server.ServerInstance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * This service provides facility for executing Runnable and Callable tasks inside a lock.
 * Note: it may be handy to provide a tryLock facility that, if locked, skip executing the Runnable or Callable and exit immediately.
 *
 * @implNote There are no expiry for locks so a service may hold a lock infinitely until the service is restarted as the
 *           liveness mechanism release all locks when the service is unreachable.
 *           This may be improved at some point by adding an expiry (for ex 30s) and running a thread that will periodically
 *           increase the expiry for all exiting locks. This should allow quicker recovery of zombie locks than relying on the liveness mechanism,
 *           as a service wanted to lock an expired lock would be able to take it over.
 */
@Slf4j
@Singleton
public class LockService {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(300);
    private static final int DEFAULT_SLEEP_MS = 1;

    private final LockRepositoryInterface lockRepository;

    @Inject
    public LockService(LockRepositoryInterface lockRepository) {
        this.lockRepository = lockRepository;
    }

    /**
     * Executes a Runnable inside a lock.
     * If the lock is already taken, it will wait for at most the default lock timeout of 5mn.
     * @see #doInLock(String, String, Duration, Runnable)
     *
     * @param category lock category, ex 'executions'
     * @param id identifier of the lock identity inside the category, ex an execution ID
     *
     * @throws LockException if the lock cannot be hold before the timeout or the thread is interrupted.
     */
    public void doInLock(String category, String id, Runnable runnable) throws LockException {
        doInLock(category, id, DEFAULT_TIMEOUT, runnable);
    }

    /**
     * Executes a Runnable inside a lock.
     * If the lock is already taken, it will wait for at most the <code>timeout</code> duration.
     * @see #doInLock(String, String, Runnable)
     *
     * @param category lock category, ex 'executions'
     * @param id identifier of the lock identity inside the category, ex an execution ID
     * @param timeout how much time to wait for the lock if another process already hold the same lock
     *
     * @throws LockException if the lock cannot be hold before the timeout or the thread is interrupted.
     */
    public void doInLock(String category, String id, Duration timeout, Runnable runnable) throws LockException {
        if (!lock(category, id, timeout)) {
            throw new LockException("Unable to hold the lock inside the configured timeout of " + timeout);
        }

        try {
            runnable.run();
        } finally {
            unlock(category, id);
        }
    }

    /**
     * Executes a Callable inside a lock.
     * If the lock is already taken, it will wait for at most the default lock timeout of 5mn.
     *
     * @param category lock category, ex 'executions'
     * @param id identifier of the lock identity inside the category, ex an execution ID
     *
     * @throws LockException if the lock cannot be hold before the timeout or the thread is interrupted.
     */
    public <T> T callInLock(String category, String id, Callable<T> callable) throws Exception {
        return callInLock(category, id, DEFAULT_TIMEOUT, callable);
    }

    /**
     * Executes a Callable inside a lock.
     * If the lock is already taken, it will wait for at most the <code>timeout</code> duration.
     *
     * @param category lock category, ex 'executions'
     * @param id identifier of the lock identity inside the category, ex an execution ID
     * @param timeout how much time to wait for the lock if another process already hold the same lock
     *
     * @throws LockException if the lock cannot be hold before the timeout or the thread is interrupted.
     */
    public <T> T callInLock(String category, String id, Duration timeout, Callable<T> callable) throws Exception {
        if (!lock(category, id, timeout)) {
            throw new LockException("Unable to hold the lock inside the configured timeout of " + timeout);
        }

        try {
            return callable.call();
        } finally {
            unlock(category, id);
        }
    }

    /**
     * Release all locks hold by this service identifier.
     */
    public List<Lock> releaseAllLocks(String serviceId) {
        return lockRepository.deleteByOwner(serviceId);
    }

    /**
     * @return true if the lock identified by this category and identifier already exist.
     */
    public boolean isLocked(String category, String id) {
        return lockRepository.findById(category, id).isPresent();
    }

    private boolean lock(String category, String id, Duration timeout) throws LockException {
        log.debug("Locking '{}'.'{}'", category,  id);
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        do {
            Optional<Lock> existing = lockRepository.findById(category, id);
            if (existing.isEmpty()) {
                // we can try to lock!
                Lock newLock = new Lock(category, id, ServerInstance.INSTANCE_ID, Instant.now());
                if (lockRepository.create(newLock)) {
                    return true;
                } else {
                    log.debug("Cannot create the lock, it may have been created after we check for its existence and before we create it");
                }
            } else {
                log.debug("Already locked by: {}", existing.get().getOwner());
            }

            try {
                Thread.sleep(DEFAULT_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LockException(e);
            }
        } while (System.currentTimeMillis() < deadline);

        log.debug("Lock already hold, waiting for it to be released");
        return false;
    }

    private void unlock(String category, String id) {
        log.debug("Unlocking '{}'.'{}'", category, id);

        Optional<Lock> existing = lockRepository.findById(category, id);
        if (existing.isEmpty()) {
            log.warn("Try to unlock unknown lock '{}'.'{}', ignoring it", category, id);
            return;
        }

        if (!existing.get().getOwner().equals(ServerInstance.INSTANCE_ID)) {
            log.warn("Try to unlock a lock we no longer own '{}'.'{}', ignoring it", category, id);
            return;
        }

        lockRepository.deleteById(category, id);
    }
}
