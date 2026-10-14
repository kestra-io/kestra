package io.kestra.worker;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerConfigTest {

    @Test
    void shouldRejectJobBufferSizeWhenNegative() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("kestra.worker.job-buffer-size", -1))) {
            assertThatThrownBy(() -> context.getBean(WorkerConfig.class))
                .hasStackTraceContaining("jobBufferSize");
        }
    }
}
