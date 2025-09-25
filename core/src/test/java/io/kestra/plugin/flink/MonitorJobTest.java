package io.kestra.plugin.flink;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class MonitorJobTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testMonitorJobCreation() {
        MonitorJob monitor = MonitorJob.builder()
            .id("test-monitor")
            .type(MonitorJob.class.getName())
            .restUrl(Property.of("http://localhost:8081"))
            .jobId(Property.of("test-job-id"))
            .waitTimeout(Property.of(Duration.ofMinutes(30)))
            .checkInterval(Property.of(Duration.ofSeconds(30)))
            .failOnError(Property.of(false))
            .build();

        assertThat(monitor.getId(), is("test-monitor"));
        assertThat(monitor.getRestUrl(), notNullValue());
        assertThat(monitor.getJobId(), notNullValue());
        assertThat(monitor.getWaitTimeout(), notNullValue());
        assertThat(monitor.getCheckInterval(), notNullValue());
        assertThat(monitor.getFailOnError(), notNullValue());
    }

    @Test
    void testMonitorJobWithExpectedStates() {
        MonitorJob monitor = MonitorJob.builder()
            .id("test-monitor-states")
            .type(MonitorJob.class.getName())
            .restUrl(Property.of("http://localhost:8081"))
            .jobId(Property.of("test-job-id"))
            .expectedTerminalStates(Property.of(Arrays.asList("FINISHED", "CANCELED")))
            .build();

        assertThat(monitor.getExpectedTerminalStates(), notNullValue());
    }

    @Test
    void testMonitorJobDefaults() {
        MonitorJob monitor = MonitorJob.builder()
            .id("test-monitor-defaults")
            .type(MonitorJob.class.getName())
            .restUrl(Property.of("http://localhost:8081"))
            .jobId(Property.of("test-job-id"))
            .build();

        // Test that defaults are properly initialized
        assertThat(monitor.getWaitTimeout(), notNullValue());
        assertThat(monitor.getCheckInterval(), notNullValue());
        assertThat(monitor.getFailOnError(), notNullValue());
    }
}