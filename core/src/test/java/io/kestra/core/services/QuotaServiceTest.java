package io.kestra.core.services;

import io.kestra.core.models.flows.FlowInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@MicronautTest
class QuotaServiceTest {
    @Inject
    private QuotaService quotaService;

    @Test
    void shouldThrowUnsupportedOperationExceptionAsQuotasAreAnEEFeature() {
        // Given
        FlowInterface flow = mock(FlowInterface.class);

        // When / Then
        assertThatThrownBy(() -> quotaService.checkAndIncrement(flow))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Quotas are an EE feature");
    }
}
