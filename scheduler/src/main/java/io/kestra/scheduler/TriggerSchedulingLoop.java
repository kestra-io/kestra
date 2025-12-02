package io.kestra.scheduler;

import io.kestra.core.scheduler.events.TriggerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The scheduling loop is responsible for periodically invoking the {@link TriggerScheduler#onSchedule} method
 * (once every second) and for processing any queued trigger events.
 */
public class TriggerSchedulingLoop implements Runnable {
    
    private static final Logger LOG = LoggerFactory.getLogger(TriggerSchedulingLoop.class);
    
    private static final long SCHEDULE_INTERVAL_MILLIS = Duration.ofSeconds(1).toMillis();
    
    private final int schedulingLoopId;
    private final TriggerScheduler triggerScheduler;
    private final Clock clock;
    
    // Queue
    private final BlockingQueue<CompletableTriggerEvent> triggerEventQueue = new LinkedBlockingQueue<>();
    private final ReentrantLock triggerEventQueueLock = new ReentrantLock();
    private final Condition notEmptyTriggerEventQueue = triggerEventQueueLock.newCondition();
    
    // Services
    private final TriggerEventHandler triggerEventHandler;
    
    // Threading
    private volatile Thread thread;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CountDownLatch stopped = new CountDownLatch(1);
    
    // Pause & Resume
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition unpaused = pauseLock.newCondition();
    
    private final BlockingQueue<Runnable> internalLoopCallables = new LinkedBlockingQueue<>();
    
    private final Set<Integer> assignments = new HashSet<>();
    
    /**
     * Creates a new {@link TriggerSchedulingLoop} instance.
     *
     * @param schedulingLoopId    the scheduling-loop identifier.
     * @param triggerScheduler    the {@link TriggerScheduler}.
     * @param triggerEventHandler the {@link TriggerEventHandler}.
     * @param clock               the {@link Clock}.
     */
    public TriggerSchedulingLoop(int schedulingLoopId,
                                 TriggerScheduler triggerScheduler,
                                 TriggerEventHandler triggerEventHandler,
                                 Clock clock) {
        this.schedulingLoopId = schedulingLoopId;
        this.triggerScheduler = triggerScheduler;
        this.triggerEventHandler = triggerEventHandler;
        this.clock = clock;
    }
    
    /**
     * Gets the identifier of this event-loop.
     *
     * @return the int identifier.
     */
    public int id() {
        return this.schedulingLoopId;
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public void run() {
        if (!this.running.compareAndSet(false, true)) {
            throw new IllegalStateException("Already running");
        }
        
        this.thread = Thread.currentThread();
        Instant nextScheduleTime = clock.instant();
        Instant tick = clock.instant();
        try {

            while (running.get()) {
                try {
                    long elapsed = clock.instant().toEpochMilli() - tick.toEpochMilli();
                    if (elapsed > (SCHEDULE_INTERVAL_MILLIS + (SCHEDULE_INTERVAL_MILLIS / 10))) {
                        // useful for debugging unexpected schedule delay
                        LOG.warn("Thread starvation, clock leap detected, or too many triggers to evaluate (elapsed since previous loop {}ms)", elapsed);
                    }
                    tick = clock.instant();
                    
                    waitIfPaused();
                    
                    // Check if the loop was stopped while being paused
                    if (!running.get()) {
                        continue;
                    }
                    
                    // Check whether vNodes are available for this event-loop
                    // The list of vNodes assignments can be empty if:
                    // 1. This scheduler is starting
                    // 2. The vNode assignments was cleared/revoked
                    if (assignments.isEmpty()) {
                        doOnEndLoop();
                        if (assignments.isEmpty()) {
                            // Small busy-loop - assignment is expected to complete in a few milliseconds.
                            Thread.sleep(Duration.ofMillis(50));
                        }
                        continue;
                    }
                    
                    final Instant now = clock.instant();
                    
                    if (!initialized.get()) {
                        triggerScheduler.onStart(clock, now, assignments);
                        initialized.set(true);
                    }
                    
                    // Process all received triggers events for current assignments.
                    processTriggerEvents();
                    
                    // Check whether triggers should be scheduled
                    if (now.isAfter(nextScheduleTime) || now.equals(nextScheduleTime)) {
                        triggerScheduler.onSchedule(clock, now, assignments);
                        nextScheduleTime = nextScheduleTime.plusMillis(SCHEDULE_INTERVAL_MILLIS);
                    }
                    
                    // Execute end-loop actions
                    doOnEndLoop();
                    
                    // May wait before next iteration
                    long waitMillis = Math.max(0, nextScheduleTime.toEpochMilli() - clock.instant().toEpochMilli());
                    if (waitMillis > 0) {
                        waitForNextIterationOrNewEvent(Duration.ofMillis(waitMillis));
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Interrupted while waiting in scheduling loop. Stopping.");
                    running.set(false);
                } catch (Exception e) {
                    LOG.error("Error in scheduling loop", e);
                }
            }
        } finally {
            stopped.countDown();
            LOG.info("[{}-{}] stopped", getClass().getSimpleName(), schedulingLoopId);
        }
    }
    
    private void waitForNextIterationOrNewEvent(final Duration duration) throws InterruptedException {
        if (triggerEventQueue.isEmpty()) {
            triggerEventQueueLock.lock();
            try {
                if (triggerEventQueue.isEmpty()) {
                    notEmptyTriggerEventQueue.await(duration.toMillis(), TimeUnit.MILLISECONDS);
                }
            } finally {
                triggerEventQueueLock.unlock();
            }
        }
    }
    
    /**
     * Stops this loop.
     * <p>
     * This method blocks until the current processing loop is completed.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            LOG.debug("[{}] stop() called but not running", getClass().getSimpleName());
            return;
        }
        
        resume(); // In case it's paused and blocked
        
        if (this.thread != null) {
            this.thread.interrupt();
            try {
                if (!stopped.await(5, TimeUnit.SECONDS)) {
                    LOG.warn("Timeout while waiting for {} to complete", this.thread.getName());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void waitIfPaused() throws InterruptedException {
        if (!paused.get()) {
            return; // return immediately
        }
        pauseLock.lock();
        try {
            while (paused.get() && running.get()) {
                LOG.info("Paused. Waiting for scheduling loop to resume");
                unpaused.await(); // Wait until resume() signals
                LOG.info("Resumed");
            }
        } finally {
            pauseLock.unlock();
        }
    }
    
    /**
     * Gets the current assignment for this event loop.
     * 
     * @return  the assignments.
     */
    public Set<Integer> assignments() {
        return assignments;
    }
    
    public void setAssignments(final Set<Integer> assignments) {
        this.assignments.clear();
        if (assignments != null) {
            this.assignments.addAll(assignments);
        }
        this.initialized.set(false);
    }
    
    /**
     * Pauses this event-loop instance.
     */
    public void pause() {
        pauseLock.lock();
        try {
            paused.set(true);
        } finally {
            pauseLock.unlock();
        }
    }
    
    /**
     * Registers an {@link Runnable action} that will be executed on next end loop.
     *
     * @param action the action to be run.
     * @return the {@link CompletableFuture}.
     */
    public CompletableFuture<Void> doOnEndLoop(final Runnable action) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        internalLoopCallables.add(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
    
    /**
     * Resumes this event-loop instance if currently paused.
     */
    public void resume() {
        pauseLock.lock();
        try {
            if (paused.compareAndSet(true, false)) {
                unpaused.signalAll();
            }
        } finally {
            pauseLock.unlock();
        }
    }
    
    /**
     *
     * @param events The trigger events.
     */
    public CompletableFuture<Void> addTriggerEvents(int vNode, List<TriggerEvent> events) {
        List<CompletableFuture<Void>> futures = events.stream().map(event -> addTriggerEvent(vNode, event)).toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     *
     * @param event The trigger event.
     */
    public CompletableFuture<Void> addTriggerEvent(int vNode, TriggerEvent event) {
        CompletableTriggerEvent completable = new CompletableTriggerEvent(event, vNode);
        this.triggerEventQueue.add(completable);
        this.triggerEventQueueLock.lock();
        try {
            notEmptyTriggerEventQueue.signal(); // wake up waiting loop if needed
        } finally {
            triggerEventQueueLock.unlock();
        }
        return completable;
    }
    
    /**
     * Processes all trigger events currently queued by this scheduling loop.
     *
     * @return the number of events processed.
     */
    public int processTriggerEvents() {
        List<CompletableTriggerEvent> drained = new ArrayList<>();
        triggerEventQueue.drainTo(drained);
        drained.forEach(item -> {
            try {
                triggerEventHandler.handle(clock, item.vnode(), item.event());
            } catch (Exception e) {
                LOG.warn("Error handling trigger event [uid={}, type={}]", item.event().uid(), item.event().type(), e);
            } finally {
                // always complete the future successfully
                item.complete(null);
            }
        });
        return drained.size();
    }
    
    private void doOnEndLoop() {
        List<Runnable> drained = new ArrayList<>();
        internalLoopCallables.drainTo(drained);
        
        for (Runnable runnable : drained) {
            runnable.run();
        }
    }
    
    /**
     * Wraps a {@link TriggerEvent} with the associated Virtual Node (vNodes).
     */
    public static class CompletableTriggerEvent extends CompletableFuture<Void> {
        
        private final TriggerEvent event;
        private final Integer vnode;
        
        public CompletableTriggerEvent(TriggerEvent event, Integer vnode) {
            this.event = Objects.requireNonNull(event, "event must not be null");
            this.vnode = Objects.requireNonNull(vnode, "vnode must not be null");
        }
        
        public TriggerEvent event() { return event; }
        
        public Integer vnode() { return vnode; }
    }
}
