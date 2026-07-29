package io.kestra.core.services;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.quota.Quota;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class QuotaServiceTest {
    private final QuotaService quotaService = new QuotaService();

    @Test
    void shouldThrowUnsupportedOperationExceptionAsQuotasAreAnEEFeature() {
        // Given
        FlowInterface flow = mock(FlowInterface.class);

        // When
        Mockito.when(flow.getQuotas()).thenReturn(List.of(Quota.builder().build()));

        // Then
        assertThatThrownBy(() -> quotaService.checkAndIncrement(flow))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Quotas are an EE feature");
    }
}
