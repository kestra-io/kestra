package io.kestra.core.services;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.FlowId;

import static org.assertj.core.api.Assertions.assertThat;

class FlowGovernanceNotifierTest {

    @Test
    void shouldScopeNotificationToNamespaceSubtreeOfTenantWhenNamespaceGiven() {
        // Given
        FlowGovernanceNotifier notifier = new FlowGovernanceNotifier();
        AtomicReference<Predicate<FlowId>> received = new AtomicReference<>();
        notifier.listen(received::set);

        // When
        notifier.notifyChanged("tenant", "acme");

        // Then
        Predicate<FlowId> inScope = received.get();
        assertThat(inScope.test(flow("tenant", "acme"))).isTrue();
        assertThat(inScope.test(flow("tenant", "acme.data"))).isTrue();
        assertThat(inScope.test(flow("tenant", "acmex"))).isFalse();
        assertThat(inScope.test(flow("other", "acme"))).isFalse();
    }

    @Test
    void shouldScopeNotificationToWholeTenantWhenNoNamespaceGiven() {
        // Given
        FlowGovernanceNotifier notifier = new FlowGovernanceNotifier();
        AtomicReference<Predicate<FlowId>> received = new AtomicReference<>();
        notifier.listen(received::set);

        // When
        notifier.notifyChanged("tenant", null);

        // Then
        Predicate<FlowId> inScope = received.get();
        assertThat(inScope.test(flow("tenant", "any.namespace"))).isTrue();
        assertThat(inScope.test(flow("other", "any.namespace"))).isFalse();
    }

    @Test
    void shouldStopNotifyingWhenListenerIsDisposed() {
        // Given
        FlowGovernanceNotifier notifier = new FlowGovernanceNotifier();
        AtomicReference<Predicate<FlowId>> received = new AtomicReference<>();
        notifier.listen(received::set).dispose();

        // When
        notifier.notifyChangedAll();

        // Then
        assertThat(received.get()).isNull();
    }

    private static FlowId flow(String tenantId, String namespace) {
        return FlowId.of(tenantId, namespace, "flow", 1);
    }
}
