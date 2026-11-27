package io.kestra.core.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.micrometer.java21.instrument.binder.jdk.VirtualThreadMetrics;
import io.micronaut.context.ApplicationContext;
import jdk.jfr.FlightRecorder;

import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
class MeterRegistryBinderFactoryTest {

    @Test
    void shouldStopTheVirtualThreadRecordingStreamWhenContextIsClosed() {
        // Given
        int recordingsBefore = activeRecordings();

        // When a context creates the binder, it opens a JFR recording stream
        try (ApplicationContext context = ApplicationContext.run()) {
            context.getBean(VirtualThreadMetrics.class);
            assertThat(activeRecordings()).isEqualTo(recordingsBefore + 1);
        }

        // Then closing the context closes the stream, so test JVMs that open many contexts don't leak one per context
        assertThat(activeRecordings()).isEqualTo(recordingsBefore);
    }

    private static int activeRecordings() {
        return FlightRecorder.isInitialized() ? FlightRecorder.getFlightRecorder().getRecordings().size() : 0;
    }
}
