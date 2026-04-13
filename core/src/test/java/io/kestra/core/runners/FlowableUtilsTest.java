package io.kestra.core.runners;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Either;
import io.kestra.plugin.core.debug.Return;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest
class FlowableUtilsTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void resolveSequentialNexts_shouldNotSkipTaskWhenPreviousFlowableProducesMultipleTaskRuns() {
        // Given
        Execution base = Execution.builder()
            .id("test-execution")
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .flowRevision(1)
            .state(new State().withState(State.Type.RUNNING))
            .build();

        ResolvedTask taskA = resolvedTask("task_a");
        ResolvedTask waitFor = resolvedTask("wait_for");
        ResolvedTask taskB = resolvedTask("task_b");
        ResolvedTask taskC = resolvedTask("task_c");

        TaskRun taskATaskRun = TaskRun.of(base, taskA).withState(State.Type.SUCCESS);
        TaskRun waitForTaskRunIter1 = TaskRun.of(base, waitFor).withState(State.Type.SUCCESS);
        TaskRun waitForTaskRunIter2 = TaskRun.of(base, waitFor).withState(State.Type.SUCCESS);
        TaskRun taskBTaskRun = TaskRun.of(base, taskB).withState(State.Type.SUCCESS);

        Execution execution = base.toBuilder()
            .taskRunList(List.of(taskATaskRun, waitForTaskRunIter1, waitForTaskRunIter2, taskBTaskRun))
            .build();

        // When
        List<NextTaskRun> next = FlowableUtils.resolveSequentialNexts(
            execution,
            List.of(taskA, waitFor, taskB, taskC)
        );

        assertThat(next).hasSize(1);
        assertThat(next.getFirst().getTaskRun().getTaskId()).isEqualTo("task_c");
    }

    @Test
    void resolveValues_withStringJsonArray_shouldReturnListOfStrings() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        // When
        Either<List<String>, List<Pair<String, String>>> result =
            FlowableUtils.resolveValues(runContext, "[\"a\", \"b\", \"c\"]");

        // Then
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).containsExactly("a", "b", "c");
    }

    @Test
    void resolveValues_withStringJsonArrayContainingDuplicates_shouldDeduplicate() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        // When
        Either<List<String>, List<Pair<String, String>>> result =
            FlowableUtils.resolveValues(runContext, "[\"a\", \"b\", \"a\"]");

        // Then
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).containsExactly("a", "b");
    }

    @Test
    void resolveValues_withStringJsonObject_shouldReturnListOfPairs() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        // When
        Either<List<String>, List<Pair<String, String>>> result =
            FlowableUtils.resolveValues(runContext, "{\"key1\": \"val1\", \"key2\": \"val2\"}");

        // Then
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).containsExactlyInAnyOrder(
            Pair.of("key1", "val1"),
            Pair.of("key2", "val2")
        );
    }

    @Test
    void resolveValues_withList_shouldReturnListOfStrings() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        // When
        Either<List<String>, List<Pair<String, String>>> result =
            FlowableUtils.resolveValues(runContext, List.of("x", "y", "z"));

        // Then
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).containsExactly("x", "y", "z");
    }

    @Test
    void resolveValues_withListContainingDuplicates_shouldDeduplicate() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        // When
        Either<List<String>, List<Pair<String, String>>> result =
            FlowableUtils.resolveValues(runContext, List.of("x", "y", "x"));

        // Then
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).containsExactly("x", "y");
    }

    @Test
    void resolveValues_withListContainingNull_shouldThrow() {
        // Given
        RunContext runContext = runContextFactory.of();
        List<Object> values = new ArrayList<>();
        values.add("a");
        values.add(null);

        // When/Then
        assertThatThrownBy(() -> FlowableUtils.resolveValues(runContext, values))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("Found a null value");
    }

    @Test
    void resolveValues_withMap_shouldReturnListOfPairs() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        // When
        Either<List<String>, List<Pair<String, String>>> result =
            FlowableUtils.resolveValues(runContext, Map.of("k1", "v1", "k2", "v2"));

        // Then
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).containsExactlyInAnyOrder(
            Pair.of("k1", "v1"),
            Pair.of("k2", "v2")
        );
    }

    @Test
    void resolveValues_withUnknownType_shouldThrow() {
        // Given
        RunContext runContext = runContextFactory.of();

        // When/Then — Integer hits the default branch and must throw
        assertThatThrownBy(() -> FlowableUtils.resolveValues(runContext, 42))
            .isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void resolveLoopValuesUri_withNonStringValue_shouldReturnEmpty() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        // When
        Optional<String> result = FlowableUtils.resolveLoopValuesUri(runContext, List.of("a", "b"));

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void resolveLoopValuesUri_withJsonArrayString_shouldReturnEmpty() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        // When
        Optional<String> result = FlowableUtils.resolveLoopValuesUri(runContext, "[\"a\", \"b\"]");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void resolveLoopValuesUri_withKestraUri_shouldReturnUri() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        // The value must be a JSON-encoded string whose text content is a supported URI
        String kestraUri = "kestra://tenant/namespace/file.ion";
        String jsonEncodedUri = "\"" + kestraUri + "\"";

        // When
        Optional<String> result = FlowableUtils.resolveLoopValuesUri(runContext, jsonEncodedUri);

        // Then
        assertThat(result).contains(kestraUri);
    }

    @Test
    void resolveLoopValuesUri_withPlainNonJsonString_shouldReturnEmpty() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        // When — a bare unquoted string is not a JSON textual node; IOException is swallowed
        Optional<String> result = FlowableUtils.resolveLoopValuesUri(runContext, "just-a-plain-string");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void resolveLoopValuesUri_withUnsupportedScheme_shouldReturnEmpty() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        // http:// is not a supported scheme
        String jsonEncodedUri = "\"http://some-host/file.ion\"";

        // When
        Optional<String> result = FlowableUtils.resolveLoopValuesUri(runContext, jsonEncodedUri);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void readAndCountLoopValuesFromUri_withNoLimit_shouldReturnAllValuesAndCorrectCount() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        URI uri = createIonFile(runContext, List.of("value1", "value2", "value3"));

        // When
        FlowableUtils.LoopInitialValuesFromUri result =
            FlowableUtils.readAndCountLoopValuesFromUri(runContext, uri.toString(), Integer.MAX_VALUE);

        // Then
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.values()).containsExactly("value1", "value2", "value3");
        // nextOffset must point past the last byte — a subsequent read at that position returns nothing
        assertThat(result.nextOffset()).isGreaterThan(0);
        Pair<List<String>, Long> followUp =
            FlowableUtils.readLoopValuesFromUri(runContext, uri.toString(), result.nextOffset(), 1);
        assertThat(followUp.getLeft()).isEmpty();
    }

    @Test
    void readAndCountLoopValuesFromUri_withLimit_shouldReturnFirstValuesButCountAll() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        URI uri = createIonFile(runContext, List.of("v1", "v2", "v3", "v4", "v5"));

        // When
        FlowableUtils.LoopInitialValuesFromUri result =
            FlowableUtils.readAndCountLoopValuesFromUri(runContext, uri.toString(), 2);

        // Then
        assertThat(result.totalCount()).isEqualTo(5);
        assertThat(result.values()).containsExactly("v1", "v2");
        assertThat(result.nextOffset()).isGreaterThan(0);
    }

    @Test
    void readLoopValuesFromUri_fromBeginning_shouldReturnRequestedValues() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        URI uri = createIonFile(runContext, List.of("a", "b", "c", "d"));

        // When
        Pair<List<String>, Long> result =
            FlowableUtils.readLoopValuesFromUri(runContext, uri.toString(), 0, 2);

        // Then
        assertThat(result.getLeft()).containsExactly("a", "b");
        assertThat(result.getRight()).isGreaterThan(0);
    }

    @Test
    void readLoopValuesFromUri_fromOffset_shouldResumeFromWhereLastReadStopped() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        URI uri = createIonFile(runContext, List.of("a", "b", "c", "d"));

        // Read the first 2 values to obtain the offset
        Pair<List<String>, Long> firstRead =
            FlowableUtils.readLoopValuesFromUri(runContext, uri.toString(), 0, 2);
        long offset = firstRead.getRight();

        // When — read the next 2 values starting at the offset
        Pair<List<String>, Long> result =
            FlowableUtils.readLoopValuesFromUri(runContext, uri.toString(), offset, 2);

        // Then
        assertThat(result.getLeft()).containsExactly("c", "d");
    }

    @Test
    void readLoopValuesFromUri_countExceedsRemaining_shouldReturnOnlyAvailable() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        URI uri = createIonFile(runContext, List.of("x", "y"));

        // When — request more values than exist
        Pair<List<String>, Long> result =
            FlowableUtils.readLoopValuesFromUri(runContext, uri.toString(), 0, 10);

        // Then
        assertThat(result.getLeft()).containsExactly("x", "y");
    }

    /** Creates an ION file from a list of strings, stores it, and returns its kestra:// URI. */
    private URI createIonFile(RunContext runContext, List<String> values) throws IOException {
        Path path = runContext.workingDir().createTempFile(".ion");
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (String value : values) {
                writer.write(JacksonMapper.ofIon().writeValueAsString(value));
                writer.newLine();
            }
        }
        return runContext.storage().putFile(path.toFile());
    }

    private static ResolvedTask resolvedTask(String id) {
        return ResolvedTask.of(
            Return.builder()
                .id(id)
                .type(Return.class.getName())
                .format(Property.ofValue(id))
                .build()
        );
    }
}
