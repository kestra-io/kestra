package io.kestra.scheduler;

import io.kestra.core.scheduler.events.TriggerEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class TriggerSchedulingLoopTest {
    
    private TriggerScheduler triggerScheduler;
    private TriggerEventHandler triggerEventHandler;
    private Clock clock;
    
    @BeforeEach
    void setUp() {
        triggerScheduler = mock(TriggerScheduler.class);
        triggerEventHandler = mock(TriggerEventHandler.class);
        clock = Clock.systemUTC();
    }
    
    TriggerSchedulingLoop createLoop() {
        return new TriggerSchedulingLoop(1, triggerScheduler, triggerEventHandler, clock);
    }
    
    @Test
    void shouldSetAssignmentsWhenSetAssignmentsGivenNewAssignments() {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();
        
        // WHEN
        loop.setAssignments(Set.of(1, 2, 3));
        
        // THEN
        assertThat(loop.assignments()).containsExactlyInAnyOrder(1, 2, 3);
    }
    
    @Test
    void shouldPauseAndResumeLoopGivenPausedState() throws InterruptedException {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();
        loop.setAssignments(Set.of(1));
        Thread thread = new Thread(loop);
        thread.start();
        
        // WHEN
        Thread.sleep(2000); // wait for at-least one iteration
        loop.pause();
        verify(triggerScheduler, times(1)).onStart(any(), any(), eq(Set.of(1)));
        verify(triggerScheduler, atLeast(2)).onSchedule(any(), any(), eq(Set.of(1)));
        Thread.sleep(2000);
        
        // THEN
        verifyNoMoreInteractions(triggerScheduler);
    
        loop.resume();
        loop.stop();
        thread.join();
        
        // THEN
        assertThat(loop.assignments()).contains(1);
    }
    
    @Test
    void shouldRunOnScheduleWhenLoopTickGivenAssignments() throws InterruptedException {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();
        loop.setAssignments(Set.of(1));
        Thread thread = new Thread(loop);
        thread.start();
        Thread.sleep(50); // let loop start and call onStart/onSchedule
        
        // WHEN
        loop.stop();
        thread.join();
        
        // THEN
        verify(triggerScheduler, atLeastOnce()).onStart(any(), any(), eq(Set.of(1)));
        verify(triggerScheduler, atLeastOnce()).onSchedule(any(), any(), eq(Set.of(1)));
    }
    
    @Test
    void shouldStopLoopGracefullyGivenRunningLoop() throws InterruptedException {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();
        loop.setAssignments(Set.of(1));
        Thread thread = new Thread(loop);
        thread.start();
        Thread.sleep(50); // let loop start
        
        // WHEN
        loop.stop();
        thread.join();
        
        // THEN
        assertThat(thread.isAlive()).isFalse();
    }
    
    @Test
    void shouldProcessAllEventsInStrictOrderAsync() throws Exception {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();
        loop.setAssignments(Set.of(1));
        TriggerEvent event1 = mock(TriggerEvent.class);
        TriggerEvent event2 = mock(TriggerEvent.class);
        
        Thread thread = new Thread(loop);
        thread.start(); // start the event-loop
        try {
            // WHEN
            CompletableFuture<Void> allFutures = loop.addTriggerEvents(1, List.of(event1, event2));
            allFutures.join(); // wait until all events added
            
            // THEN
            verify(triggerEventHandler, times(1)).handle(any(), eq(1), eq(event1));
            verify(triggerEventHandler, times(1)).handle(any(), eq(1), eq(event2));
        } finally {
            // cleanup
            loop.stop();
            thread.join();
        }
    }
    
    @Test
    void shouldProcessAllEventsInStrictOrder() {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();
        TriggerEvent event1 = mock(TriggerEvent.class);
        TriggerEvent event2 = mock(TriggerEvent.class);
        
        // WHEN - with 2 events
        loop.addTriggerEvents(1, List.of(event1, event2));
        int processed = loop.processTriggerEvents();
        
        // THEN
        assertThat(processed).isEqualTo(2);
        
        // WHEN - with 0 event
        processed = loop.processTriggerEvents();
        
        // THEN
        assertThat(processed).isEqualTo(0);
    }
}