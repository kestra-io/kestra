package io.kestra.core.models.flows;

import java.util.List;
import java.util.Set;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.tasks.WorkerQueueFallback;
import io.kestra.core.models.tasks.WorkerSelector;
import io.kestra.core.models.tasks.Task;
import io.kestra.plugin.core.log.Log;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class AbstractFlowUsingTest {

    @Inject
    private Validator validator;

    @Test
    void shouldAcceptValidTagsOnFlow() {
        Flow flow = Flow.builder()
            .id("test")
            .namespace("io.kestra.tests")
            .workerSelector(new WorkerSelector(List.of("docker", "linux-amd64"), null))
            .tasks(List.<Task>of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()))
            .build();

        Set<ConstraintViolation<Flow>> violations = validator.validate(flow);
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectTagWithUppercase() {
        Flow flow = Flow.builder()
            .id("test")
            .namespace("io.kestra.tests")
            .workerSelector(new WorkerSelector(List.of("Docker"), null))
            .tasks(List.<Task>of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()))
            .build();

        Set<ConstraintViolation<Flow>> violations = validator.validate(flow);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().startsWith("workerSelector.tags"));
    }

    @Test
    void shouldRejectTagWithInvalidChars() {
        Flow flow = Flow.builder()
            .id("test")
            .namespace("io.kestra.tests")
            .workerSelector(new WorkerSelector(List.of("linux_amd64"), null))
            .tasks(List.<Task>of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()))
            .build();

        Set<ConstraintViolation<Flow>> violations = validator.validate(flow);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().startsWith("workerSelector.tags"));
    }

    @Test
    void shouldRejectTagWithLeadingHyphen() {
        Flow flow = Flow.builder()
            .id("test")
            .namespace("io.kestra.tests")
            .workerSelector(new WorkerSelector(List.of("-docker"), null))
            .tasks(List.<Task>of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()))
            .build();

        Set<ConstraintViolation<Flow>> violations = validator.validate(flow);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().startsWith("workerSelector.tags"));
    }

    @Test
    void shouldRejectMoreThanTwentyTags() {
        List<String> tags = java.util.stream.IntStream.range(0, 21).mapToObj(i -> "tag" + i).toList();
        Flow flow = Flow.builder()
            .id("test")
            .namespace("io.kestra.tests")
            .workerSelector(new WorkerSelector(tags, null))
            .tasks(List.<Task>of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()))
            .build();

        Set<ConstraintViolation<Flow>> violations = validator.validate(flow);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("workerSelector.tags"));
    }

    @Test
    void shouldRejectFallbackWithoutTags() {
        Flow flow = Flow.builder()
            .id("test")
            .namespace("io.kestra.tests")
            .workerSelector(new WorkerSelector(List.of(), WorkerQueueFallback.WAIT))
            .tasks(List.<Task>of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()))
            .build();

        Set<ConstraintViolation<Flow>> violations = validator.validate(flow);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("workerSelector"));
    }
}
