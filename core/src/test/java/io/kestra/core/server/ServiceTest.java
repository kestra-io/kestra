package io.kestra.core.server;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ServiceTest {

    @Test
    void shouldReturnValidTransitionForRunning() {
        List<Service.ServiceState> states = List.of(
            Service.ServiceState.RUNNING,
            Service.ServiceState.DISCONNECTED,
            Service.ServiceState.TERMINATING
        );
        states.forEach(status -> Assertions.assertTrue(Service.ServiceState.RUNNING.isValidTransition(status)));
    }

    @Test
    void shouldReturnValidTransitionForDisconnected() {
        List<Service.ServiceState> states = List.of(
            Service.ServiceState.DISCONNECTED,
            Service.ServiceState.TERMINATING,
            Service.ServiceState.NOT_RUNNING
        );
        states.forEach(status -> Assertions.assertTrue(Service.ServiceState.DISCONNECTED.isValidTransition(status)));
    }

    @Test
    void shouldReturnValidTransitionForPendingShutdown() {
        List<Service.ServiceState> states = List.of(
            Service.ServiceState.TERMINATING,
            Service.ServiceState.TERMINATED_FORCED,
            Service.ServiceState.TERMINATED_GRACEFULLY
        );
        states.forEach(status -> Assertions.assertTrue(Service.ServiceState.TERMINATING.isValidTransition(status)));
    }

    @Test
    void shouldReturnValidTransitionForForcedShutdown() {
        List<Service.ServiceState> states = List.of(
            Service.ServiceState.TERMINATED_FORCED,
            Service.ServiceState.NOT_RUNNING
        );
        states.forEach(status -> Assertions.assertTrue(Service.ServiceState.TERMINATED_FORCED.isValidTransition(status)));
    }

    @Test
    void shouldReturnValidTransitionForGracefulShutdown() {
        List<Service.ServiceState> states = List.of(
            Service.ServiceState.TERMINATED_GRACEFULLY,
            Service.ServiceState.NOT_RUNNING
        );
        states.forEach(status -> Assertions.assertTrue(Service.ServiceState.TERMINATED_GRACEFULLY.isValidTransition(status)));
    }

    @Test
    void shouldReturnValidTransitionForNotRunning() {
        List<Service.ServiceState> states = List.of(Service.ServiceState.EMPTY);
        states.forEach(status -> Assertions.assertTrue(Service.ServiceState.NOT_RUNNING.isValidTransition(status)));
    }

    @Test
    void shouldReturnTrueForDisconnectedOrPendingShutDown() {
        Assertions.assertTrue(Service.ServiceState.DISCONNECTED.isDisconnectedOrTerminating());
        Assertions.assertTrue(Service.ServiceState.TERMINATING.isDisconnectedOrTerminating());
    }
}