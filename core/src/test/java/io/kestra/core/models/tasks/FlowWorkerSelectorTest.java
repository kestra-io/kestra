package io.kestra.core.models.tasks;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class FlowWorkerSelectorTest {
    @Inject
    private ModelValidator modelValidator;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldAcceptExpressionsButRejectInvalidLiteralTags() throws JsonProcessingException {
        assertThat(modelValidator.isValid(parse("tags: [gpu, '{{ inputs.worker }}']"))).isEmpty();
        assertThat(modelValidator.isValid(parse("tags: '{{ inputs.workers }}'"))).isEmpty();
        assertThat(modelValidator.isValid(parse("tags: [GPU]"))).hasValueSatisfying(e -> assertThat(e.getMessage()).contains("GPU"));
    }

    @Test
    void shouldRejectMoreThanTwentyLiteralTags() {
        List<String> tags = IntStream.range(0, 21).mapToObj(i -> "tag" + i).toList();

        assertThat(modelValidator.isValid(new FlowWorkerSelector(tags, null))).isPresent();
    }

    @Test
    void shouldRejectFallbackWithoutTags() {
        assertThat(modelValidator.isValid(new FlowWorkerSelector(List.of(), WorkerQueueFallback.WAIT))).isPresent();
    }

    @Test
    void shouldRenderExpressionTags() throws Exception {
        FlowWorkerSelector selector = parse("""
            tags: [linux, '{{ inputs.worker }}']
            match: ANY
            fallback: WAIT
            """);

        WorkerSelector rendered = selector.render(runContext(Map.of("worker", "gpu")));

        assertThat(rendered).isEqualTo(new WorkerSelector(List.of("linux", "gpu"), WorkerSelectorMatch.ANY, WorkerQueueFallback.WAIT));
    }

    @Test
    void shouldRenderTagsExpressionToAList() throws Exception {
        FlowWorkerSelector selector = parse("tags: '{{ inputs.workers }}'");

        WorkerSelector rendered = selector.render(runContext(Map.of("workers", List.of("gpu", "linux"))));

        assertThat(rendered.tags()).containsExactly("gpu", "linux");
    }

    @Test
    void shouldDropTagsRenderingToBlankValue() throws Exception {
        FlowWorkerSelector selector = parse("tags: ['{{ inputs.worker ?? null }}', '{{ inputs.empty }}']");

        WorkerSelector rendered = selector.render(runContext(Map.of("empty", "")));

        assertThat(rendered.tags()).isEmpty();
    }

    @Test
    void shouldFailWhenTagRendersToInvalidLabel() throws JsonProcessingException {
        FlowWorkerSelector selector = parse("tags: ['{{ inputs.worker }}']");

        assertThatThrownBy(() -> selector.render(runContext(Map.of("worker", "Not_A_Label"))))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("Not_A_Label");
    }

    @Test
    void shouldFailWhenRenderingMoreThanTwentyTags() throws JsonProcessingException {
        FlowWorkerSelector selector = parse("tags: '{{ inputs.workers }}'");
        List<String> workers = IntStream.range(0, 21).mapToObj(i -> "tag" + i).toList();

        assertThatThrownBy(() -> selector.render(runContext(Map.of("workers", workers))))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("21");
    }

    private static FlowWorkerSelector parse(String yaml) throws JsonProcessingException {
        return JacksonMapper.ofYaml().readValue(yaml, FlowWorkerSelector.class);
    }

    private RunContext runContext(Map<String, Object> inputs) {
        return runContextFactory.of(Map.of("inputs", inputs));
    }
}
