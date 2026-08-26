package io.kestra.core.server;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTypeTest {

    private static final Set<ServiceType> WORKER_TYPES = EnumSet.of(ServiceType.WORKER, ServiceType.SYSTEM_WORKER);

    @ParameterizedTest
    @EnumSource(ServiceType.class)
    void shouldBeWorkerOnlyForWorkerTypes(final ServiceType type) {
        // Given / When / Then
        // Liveness handling relies on this to re-emit the jobs of any crashed worker flavour,
        // so a newly added worker type must be declared in WORKER_TYPES too.
        assertThat(type.isWorker()).isEqualTo(WORKER_TYPES.contains(type));
    }

    @Test
    void shouldParseSystemWorkerIgnoringCase() {
        // Given / When / Then
        assertThat(ServiceType.fromString("system_worker")).isEqualTo(ServiceType.SYSTEM_WORKER);
    }

    @Test
    void shouldReturnInvalidWhenParsingUnknownType() {
        // Given / When / Then
        assertThat(ServiceType.fromString("NOT_A_SERVICE")).isEqualTo(ServiceType.INVALID);
    }
}
