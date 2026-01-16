package io.kestra.core.runners;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.input.FileInput;
import io.kestra.core.models.flows.input.InputAndValue;
import io.kestra.core.models.flows.input.IntInput;
import io.kestra.core.models.flows.input.MultiselectInput;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.secret.SecretNotFoundException;
import io.kestra.core.secret.SecretService;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValue;
import io.kestra.core.utils.IdUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class FlowInputOutputTest {

    private static final String TEST_SECRET_VALUE = "test-secret-value";
    private static final String TEST_KV_VALUE = "test-kv-value";

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

    @Inject
    KvMetadataRepositoryInterface kvMetadataRepository;

    @MockBean(SecretService.class)
    SecretService testSecretService() {
        return new SecretService() {
            @Override
            public String findSecret(String tenantId, String namespace, String key) throws SecretNotFoundException {
                return TEST_SECRET_VALUE;
            }
        };
    }

    @MockBean(KVStoreService.class)
    KVStoreService testKVStoreService() {
        return new KVStoreService() {
            @Override
            public KVStore get(String tenant, String namespace, @Nullable String fromNamespace) {
                return new InternalKVStore(tenant, namespace, storageInterface, kvMetadataRepository) {
                    @Override
                    public Optional<KVValue> getValue(String key) {
                        return Optional.of(new KVValue(TEST_KV_VALUE));
                    }
                };
            }
        };
    }

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
        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, null, DEFAULT_TEST_EXECUTION, data);

        // Then
        Assertions.assertEquals(
            List.of(
                new InputAndValue(input1, "value1", true, false, null),
                new InputAndValue(input2, "value2", true, false, null)),
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
        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, null, DEFAULT_TEST_EXECUTION, data);

        // Then
        Assertions.assertEquals(
            List.of(
                new InputAndValue(input1, "v1", true, false, null),
                new InputAndValue(input2, "v2", true, false, null),
                new InputAndValue(input3, "v3", true, false, null)),
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
        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, null, DEFAULT_TEST_EXECUTION, data);

        // Then
        Assertions.assertEquals(
            List.of(
                new InputAndValue(input1, "v1", true, false, null),
                new InputAndValue(input2, "v2", false, false, null),
                new InputAndValue(input3, "v3", false, false, null)),
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
        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, null, DEFAULT_TEST_EXECUTION, data);

        // Then
        Assertions.assertEquals(
            List.of(
                new InputAndValue(input1, "value1", true, false, null),
                new InputAndValue(input2, "value2", false, false, null)),
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
        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, null, DEFAULT_TEST_EXECUTION, data);

        // Then
        Assertions.assertEquals(2, values.size());
        Assertions.assertFalse(values.get(1).enabled());
        Assertions.assertNotNull(values.get(1).exceptions());
    }

    @Test
    void shouldNotUploadFileInputAfterValidation() {
        // Given
        FileInput input = FileInput
            .builder()
            .id("input")
            .type(Type.FILE)
            .build();

        Publisher<CompletedPart> data = Mono.just(new MemoryCompletedFileUpload("input", "input", "???".getBytes(StandardCharsets.UTF_8)));

        // When
        List<InputAndValue> values = flowInputOutput.validateExecutionInputs(List.of(input), null, DEFAULT_TEST_EXECUTION, data).block();

        // Then
        Assertions.assertNull(values.getFirst().exceptions());
        Assertions.assertFalse(storageInterface.exists(MAIN_TENANT, null, URI.create(values.getFirst().value().toString())));
    }

    @Test
    void resolveInputsWithStrictDefaultTyping() {
        // Given
        StringInput input1 = StringInput.builder()
            .id("input1")
            .type(Type.STRING)
            .validator("\\d")
            .defaults(Property.ofValue("0"))
            .required(false)
            .build();
        IntInput input2 = IntInput.builder()
            .type(Type.INT)
            .id("input2")
            .defaults(Property.ofValue(0))
            .required(false)
            .build();

        List<Input<?>> inputs = List.of(input1, input2);

        Map<String, Object> data = Map.of("input42", "foo");

        // When
        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, null, DEFAULT_TEST_EXECUTION, data);

        // Then
        Assertions.assertEquals(
            List.of(
                new InputAndValue(input1, "0", true, true, null),
                new InputAndValue(input2, 0, true, true, null)),
            values
        );
    }

    @Test
    void resolveInputsGivenDefaultExpressions() {
        // Given
        StringInput input1 = StringInput.builder()
            .id("input1")
            .type(Type.STRING)
            .defaults(Property.ofExpression("{{ 'hello' }}"))
            .required(false)
            .build();
        StringInput input2 = StringInput.builder()
            .id("input2")
            .type(Type.STRING)
            .defaults(Property.ofExpression("{{ inputs.input1 }}_world"))
            .required(false)
            .dependsOn(new DependsOn(List.of("input1"),null))
            .build();

        List<Input<?>> inputs = List.of(input1, input2);

        Map<String, Object> data = Map.of("input42", "foo");

        // When
        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, null, DEFAULT_TEST_EXECUTION, data);

        // Then
        Assertions.assertEquals(
            List.of(
                new InputAndValue(input1, "hello", true, true, null),
                new InputAndValue(input2, "hello_world", true, true, null)),
            values
        );
    }

    @Test
    void shouldObfuscateSecretsWhenValidatingInputs() {
        // Given
        StringInput input = StringInput.builder()
            .id("input")
            .type(Type.STRING)
            .defaults(Property.ofExpression("{{ secret('???') }}"))
            .required(false)
            .build();

        // When
        List<InputAndValue> results = flowInputOutput.validateExecutionInputs(List.of(input), null, DEFAULT_TEST_EXECUTION, Mono.empty()).block();

        // Then
        Assertions.assertEquals("******", results.getFirst().value());
    }

    @Test
    void shouldNotObfuscateSecretsInSelectWhenValidatingInputs() {
        // Given
        MultiselectInput input = MultiselectInput.builder()
            .id("input")
            .type(Type.MULTISELECT)
            .expression("{{ [secret('???')] }}")
            .required(false)
            .build();

        // When
        List<InputAndValue> results = flowInputOutput.validateExecutionInputs(List.of(input), null, DEFAULT_TEST_EXECUTION, Mono.empty()).block();

        // Then
        Assertions.assertEquals(TEST_SECRET_VALUE, ((MultiselectInput)results.getFirst().input()).getValues().getFirst());
    }

    @Test
    void shouldNotObfuscateSecretsWhenReadingInputs() {
        // Given
        StringInput input = StringInput.builder()
            .id("input")
            .type(Type.STRING)
            .defaults(Property.ofExpression("{{ secret('???') }}"))
            .required(false)
            .build();

        // When
        Map<String, Object> results = flowInputOutput.readExecutionInputs(List.of(input), null, DEFAULT_TEST_EXECUTION, Mono.empty()).block();

        // Then
        Assertions.assertEquals(TEST_SECRET_VALUE, results.get("input"));
    }

    @Test
    void shouldEvaluateExpressionOnDefaultsUsingKVFunction() {
        // Given
        StringInput input = StringInput.builder()
            .id("input")
            .type(Type.STRING)
            .defaults(Property.ofExpression("{{ kv('???') }}"))
            .required(false)
            .build();

        // When
        Map<String, Object> results = flowInputOutput.readExecutionInputs(List.of(input), null, DEFAULT_TEST_EXECUTION, Mono.empty()).block();

        // Then
        assertThat(results.get("input")).isEqualTo(TEST_KV_VALUE);
    }

    @Test
    void shouldGetDefaultWhenPassingNoDataForRequiredInput() {
        // Given
        StringInput input = StringInput.builder()
            .id("input")
            .type(Type.STRING)
            .defaults(Property.ofValue("default"))
            .build();

        // When
        Map<String, Object> results = flowInputOutput.readExecutionInputs(List.of(input), null, DEFAULT_TEST_EXECUTION, Mono.empty()).block();

        // Then
        assertThat(results.get("input")).isEqualTo("default");
    }

    @Test
    void shouldResolveZeroByteFileUpload() throws java.io.IOException {
        File tempFile = File.createTempFile("empty", ".txt");
        tempFile.deleteOnExit();

        io.micronaut.http.multipart.CompletedFileUpload fileUpload = org.mockito.Mockito.mock(io.micronaut.http.multipart.CompletedFileUpload.class);
        org.mockito.Mockito.when(fileUpload.getInputStream()).thenReturn(new java.io.FileInputStream(tempFile));
        org.mockito.Mockito.when(fileUpload.getFilename()).thenReturn("empty.txt");
        org.mockito.Mockito.when(fileUpload.getName()).thenReturn("empty_file");

        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .tenantId("unit_test_tenant")
            .namespace("io.kestra.unittest")
            .flowId("unittest")
            .flowRevision(1)
            .state(new State())
            .build();

        reactor.core.publisher.Mono<Map<String, Object>> result = flowInputOutput.readExecutionInputs(
            List.of(
                io.kestra.core.models.flows.input.FileInput.builder().id("empty_file").type(Type.FILE).build()
            ),
            Flow.builder().id("unittest").namespace("io.kestra.unittest").build(),
            execution,
            reactor.core.publisher.Flux.just(fileUpload)
        );

        Map<String, Object> outputs = result.block();

        Assertions.assertNotNull(outputs);
        Assertions.assertTrue(outputs.containsKey("empty_file"));
    }

    private static class MemoryCompletedPart implements CompletedPart {

        protected final String name;
        protected final byte[] content;

        public MemoryCompletedPart(String name, byte[] content) {
            this.name = name;
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
    }

    private static final class MemoryCompletedFileUpload extends MemoryCompletedPart implements CompletedFileUpload {

        private final String fileName;

        public MemoryCompletedFileUpload(String name, String fileName, byte[] content) {
            super(name, content);
            this.fileName = fileName;
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

        @Override
        public void discard() {
        }
    }
}
