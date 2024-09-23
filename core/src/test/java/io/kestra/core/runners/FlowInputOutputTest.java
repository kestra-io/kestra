package io.kestra.core.runners;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.DependsOn;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.input.FileInput;
import io.kestra.core.models.flows.input.InputAndValue;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@KestraTest
class FlowInputOutputTest {

    static final Execution DEFAULT_TEST_EXECUTION = Execution.builder()
        .id(IdUtils.create())
        .flowId(IdUtils.create())
        .flowRevision(1)
        .namespace("io.kestra.test")
        .build();

    @Inject
    FlowInputOutput flowInputOutput;

    @Inject
    StorageInterface storageInterface;

    @Test
    void shouldResolveEnabledInputsGivenInputWithConditionalExpressionMatchingTrue() {
        // Given

        StringInput input1 = StringInput.builder()
            .id("input1")
            .build();
        StringInput input2 = StringInput.builder()
            .id("input2")
            .dependsOn(new DependsOn(
                List.of("input1"),
                "{{ inputs.input1 equals 'value1' }}"))
            .build();

        List<Input<?>> inputs = List.of(input1, input2);

        Map<String, Object> data = Map.of("input1", "value1", "input2", "value2");

        // When
        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, DEFAULT_TEST_EXECUTION, data);

        // Then
        Assertions.assertEquals(
            List.of(
                new InputAndValue(input1, "value1", true, null),
                new InputAndValue(input2, "value2", true, null)),
            values
        );
    }

    @Test
    void shouldResolveEnabledInputsGivenInputWithConditionalInputTrue() {
        // Given

        StringInput input1 = StringInput.builder()
            .id("input1")
            .build();
        // ENABLED
        StringInput input2 = StringInput.builder()
            .id("input2")
            .dependsOn(new DependsOn(List.of("input1"), "{{ inputs.input1 equals 'v1' }}"))
            .build();
        // ENABLED
        StringInput input3 = StringInput.builder()
            .id("input3")
            .dependsOn(new DependsOn(List.of("input2"), null))
            .build();
        List<Input<?>> inputs = List.of(input1, input2, input3);

        Map<String, Object> data = Map.of("input1", "v1", "input2", "v2", "input3", "v3");

        // When
        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, DEFAULT_TEST_EXECUTION, data);

        // Then
        Assertions.assertEquals(
            List.of(
                new InputAndValue(input1, "v1", true, null),
                new InputAndValue(input2, "v2", true, null),
                new InputAndValue(input3, "v3", true, null)),
            values
        );
    }

    @Test
    void shouldResolveDisabledInputsGivenInputWithConditionalInputFalse() {
        // Given

        StringInput input1 = StringInput.builder()
            .id("input1")
            .build();
        // DISABLED
        StringInput input2 = StringInput.builder()
            .id("input2")
            .dependsOn(new DependsOn(List.of("input1"), "{{ inputs.input1 equals '???' }}"))
            .build();
        // DISABLED
        StringInput input3 = StringInput.builder()
            .id("input3")
            .dependsOn(new DependsOn(List.of("input2"), null))
            .build();
        List<Input<?>> inputs = List.of(input1, input2, input3);

        Map<String, Object> data = Map.of("input1", "v1", "input2", "v2", "input3", "v3");

        // When
        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, DEFAULT_TEST_EXECUTION, data);

        // Then
        Assertions.assertEquals(
            List.of(
                new InputAndValue(input1, "v1", true, null),
                new InputAndValue(input2, "v2", false, null),
                new InputAndValue(input3, "v3", false, null)),
            values
        );
    }

    @Test
    void shouldResolveDisabledInputsGivenInputWithConditionalExpressionMatchingFalse() {
        // Given
        StringInput input1 = StringInput.builder()
            .id("input1")
            .build();
        StringInput input2 = StringInput.builder()
            .id("input2")
            .dependsOn(new DependsOn(
                List.of("input1"),
                "{{ inputs.input1 equals 'dummy' }}"))
            .build();

        List<Input<?>> inputs = List.of(input1, input2);

        Map<String, Object> data = Map.of("input1", "value1", "input2", "value2");

        // When
        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, DEFAULT_TEST_EXECUTION, data);

        // Then
        Assertions.assertEquals(
            List.of(
                new InputAndValue(input1, "value1", true, null),
                new InputAndValue(input2, "value2", false, null)),
            values
        );
    }

    @Test
    void shouldResolveDisabledInputsGivenInputWithErroneousConditionalExpression() {
        // Given
        StringInput input1 = StringInput.builder()
            .id("input1")
            .build();
        StringInput input2 = StringInput.builder()
            .id("input2")
            .dependsOn(new DependsOn(
                List.of("input1"),
                "{{ inputs.dummy equals 'dummy' }}"))
            .build();

        List<Input<?>> inputs = List.of(input1, input2);

        Map<String, Object> data = Map.of("input1", "value1", "input2", "value2");

        // When
        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, DEFAULT_TEST_EXECUTION, data);

        // Then
        Assertions.assertEquals(2, values.size());
        Assertions.assertFalse(values.get(1).enabled());
        Assertions.assertNotNull(values.get(1).exception());
    }

    @Test
    void shouldDeleteFileInputAfterValidationGivenDeleteTrue() throws IOException {
        // Given
        FileInput input = FileInput.builder()
            .id("input")
            .build();

        Publisher<CompletedPart> data = Mono.just(new MemoryCompletedFileUpload("input", "input", "???".getBytes(StandardCharsets.UTF_8)));

        // When
        List<InputAndValue> values = flowInputOutput.validateExecutionInputs(List.of(input), DEFAULT_TEST_EXECUTION, data, true);

        // Then
        Assertions.assertFalse(storageInterface.exists(null, URI.create(values.get(0).value().toString())));
    }

    @Test
    void shouldNotDeleteFileInputAfterValidationGivenDeleteFalse() throws IOException {
        // Given
        FileInput input = FileInput.builder()
            .id("input")
            .build();

        Publisher<CompletedPart> data = Mono.just(new MemoryCompletedFileUpload("input", "input", "???".getBytes(StandardCharsets.UTF_8)));

        // When
        List<InputAndValue> values = flowInputOutput.validateExecutionInputs(List.of(input), DEFAULT_TEST_EXECUTION, data, false);

        // Then
        Assertions.assertTrue(storageInterface.exists(null, URI.create(values.get(0).value().toString())));
    }

    private static final class MemoryCompletedFileUpload implements CompletedFileUpload {

        private final String name;
        private final String fileName;
        private final byte[] content;

        public MemoryCompletedFileUpload(String name, String fileName, byte[] content) {
            this.name = name;
            this.fileName = fileName;
            this.content = content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public ByteBuffer getByteBuffer() {
            return ByteBuffer.wrap(content);
        }

        @Override
        public Optional<MediaType> getContentType() {
            return Optional.empty();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getFilename() {
            return fileName;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public long getDefinedSize() {
            return content.length;
        }

        @Override
        public boolean isComplete() {
            return true;
        }
    }
}