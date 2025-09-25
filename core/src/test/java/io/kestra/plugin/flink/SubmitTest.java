package io.kestra.plugin.flink;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class SubmitTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testSubmitTaskCreation() {
        Submit submit = Submit.builder()
            .id("test-submit")
            .type(Submit.class.getName())
            .restUrl(Property.of("http://localhost:8081"))
            .jarUri(Property.of("file:///path/to/job.jar"))
            .entryClass(Property.of("com.example.Main"))
            .args(Property.of(Arrays.asList("--input", "test")))
            .parallelism(Property.of(4))
            .build();

        assertThat(submit.getId(), is("test-submit"));
        // Just test that properties are properly set - we can't test .as() without a RunContext
        assertThat(submit.getRestUrl(), notNullValue());
        assertThat(submit.getJarUri(), notNullValue());
        assertThat(submit.getEntryClass(), notNullValue());
        assertThat(submit.getParallelism(), notNullValue());
    }

    @Test
    @EnabledIfSystemProperty(named = "flink.integration.test", matches = "true")
    void testSubmitTaskExecution() throws Exception {
        // This test requires a running Flink cluster
        Submit submit = Submit.builder()
            .id("test-submit")
            .type(Submit.class.getName())
            .restUrl(Property.of("http://localhost:8081"))
            .jarUri(Property.of("file:///tmp/test-job.jar"))
            .entryClass(Property.of("com.example.TestJob"))
            .args(Property.of(Arrays.asList("--test-mode")))
            .parallelism(Property.of(1))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, submit, new HashMap<>());
        Submit.Output output = submit.run(runContext);

        assertThat(output.getJobId(), notNullValue());
        assertThat(output.getJarId(), notNullValue());
    }

    @Test
    void testSubmitTaskWithSavepoint() {
        Submit submit = Submit.builder()
            .id("test-submit-savepoint")
            .type(Submit.class.getName())
            .restUrl(Property.of("http://localhost:8081"))
            .jarUri(Property.of("file:///path/to/job.jar"))
            .entryClass(Property.of("com.example.Main"))
            .restoreFromSavepoint(Property.of("s3://savepoints/latest"))
            .allowNonRestoredState(Property.of(true))
            .build();

        assertThat(submit.getRestoreFromSavepoint(), notNullValue());
        assertThat(submit.getAllowNonRestoredState(), notNullValue());
    }

    @Test
    void testSubmitTaskWithJobConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("execution.checkpointing.interval", "30s");
        config.put("state.backend", "rocksdb");

        Submit submit = Submit.builder()
            .id("test-submit-config")
            .type(Submit.class.getName())
            .restUrl(Property.of("http://localhost:8081"))
            .jarUri(Property.of("file:///path/to/job.jar"))
            .entryClass(Property.of("com.example.Main"))
            .jobConfig(Property.of(config))
            .build();

        assertThat(submit.getJobConfig(), notNullValue());
    }
}