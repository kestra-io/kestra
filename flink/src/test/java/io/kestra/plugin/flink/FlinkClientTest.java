package io.kestra.plugin.flink;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlinkClientTest {

    private RunContext runContext;
    private FlinkConnection connection;

    @BeforeEach
    void setUp() {
        // Mock RunContext
        runContext = mock(DefaultRunContext.class);
        
        // Create a test connection
        connection = FlinkConnection.builder()
            .url(Property.of("http://localhost:8081"))
            .timeout(Property.of(Duration.ofMinutes(5)))
            .build();
    }

    @Test
    void testFlinkConnectionConfiguration() {
        assertNotNull(connection);
        assertEquals("http://localhost:8081", connection.getUrl().as(String.class).orElse(null));
        assertEquals(Duration.ofMinutes(5), connection.getTimeout().as(Duration.class).orElse(null));
    }

    @Test
    void testSubmitTaskConfiguration() {
        Submit submit = Submit.builder()
            .connection(connection)
            .jarUri(Property.of("s3://bucket/job.jar"))
            .entryClass(Property.of("com.example.Job"))
            .parallelism(Property.of(4))
            .build();

        assertNotNull(submit);
        assertEquals("s3://bucket/job.jar", submit.getJarUri().as(String.class).orElse(null));
        assertEquals("com.example.Job", submit.getEntryClass().as(String.class).orElse(null));
        assertEquals(Integer.valueOf(4), submit.getParallelism().as(Integer.class).orElse(null));
    }

    @Test
    void testMonitorJobConfiguration() {
        MonitorJob monitor = MonitorJob.builder()
            .connection(connection)
            .jobId(Property.of("test-job-id"))
            .waitTimeout(Property.of(Duration.ofMinutes(30)))
            .pollInterval(Property.of(Duration.ofSeconds(10)))
            .build();

        assertNotNull(monitor);
        assertEquals("test-job-id", monitor.getJobId().as(String.class).orElse(null));
        assertEquals(Duration.ofMinutes(30), monitor.getWaitTimeout().as(Duration.class).orElse(null));
        assertEquals(Duration.ofSeconds(10), monitor.getPollInterval().as(Duration.class).orElse(null));
    }

    @Test
    void testCancelTaskConfiguration() {
        Cancel cancel = Cancel.builder()
            .connection(connection)
            .jobId(Property.of("test-job-id"))
            .withSavepoint(Property.of(true))
            .savepointDir(Property.of("s3://bucket/savepoints"))
            .build();

        assertNotNull(cancel);
        assertEquals("test-job-id", cancel.getJobId().as(String.class).orElse(null));
        assertTrue(cancel.getWithSavepoint().as(Boolean.class).orElse(false));
        assertEquals("s3://bucket/savepoints", cancel.getSavepointDir().as(String.class).orElse(null));
    }

    @Test
    void testTriggerSavepointConfiguration() {
        TriggerSavepoint savepoint = TriggerSavepoint.builder()
            .connection(connection)
            .jobId(Property.of("test-job-id"))
            .targetDirectory(Property.of("s3://bucket/savepoints"))
            .cancelJob(Property.of(false))
            .timeout(Property.of(Duration.ofMinutes(10)))
            .build();

        assertNotNull(savepoint);
        assertEquals("test-job-id", savepoint.getJobId().as(String.class).orElse(null));
        assertEquals("s3://bucket/savepoints", savepoint.getTargetDirectory().as(String.class).orElse(null));
        assertFalse(savepoint.getCancelJob().as(Boolean.class).orElse(true));
        assertEquals(Duration.ofMinutes(10), savepoint.getTimeout().as(Duration.class).orElse(null));
    }
}