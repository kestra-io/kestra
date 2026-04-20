package io.kestra.core.runners;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.DependsOn;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.BoolInput;
import io.kestra.core.models.flows.input.SecretInput;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.encryption.EncryptionService;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.property.PropertyContext;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.runners.pebble.PebbleEngineFactory;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValue;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import java.security.GeneralSecurityException;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class RunVariablesTest {

    @Inject
    VariableRenderer renderer;

    @Inject
    StorageInterface storageInterface;

    @Inject
    KvMetadataRepositoryInterface kvMetadataRepository;

    @MockBean(KVStoreService.class)
    KVStoreService testKVStoreService() {
        return new KVStoreService() {
            @Override
            public KVStore get(String tenant, String namespace, @Nullable String fromNamespace) {
                return new InternalKVStore(tenant, namespace, storageInterface, kvMetadataRepository) {
                    @Override
                    public Optional<KVValue> getValue(String key) {
                        return Optional.of(new KVValue("value"));
                    }
                };
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetEmptyVariables() {
        Map<String, Object> variables = new RunVariables.DefaultBuilder().build(new RunContextLogger(), PropertyContext.create(renderer));
        assertThat(variables.size()).isEqualTo(3);
        assertThat((Map<String, Object>) variables.get("envs")).isEqualTo(Map.of());
        assertThat((Map<String, Object>) variables.get("globals")).isEqualTo(Map.of());
        assertThat(variables.get("addSecretConsumer")).isNotNull();
    }

    @Test
    void shouldGetVariablesGivenFlowWithNoTenant() {
        Map<String, Object> variables = new RunVariables.DefaultBuilder()
            .withFlow(
                Flow
                    .builder()
                    .id("id-value")
                    .namespace("namespace-value")
                    .revision(42)
                    .build()
            )
            .build(new RunContextLogger(), PropertyContext.create(renderer));
        Assertions.assertEquals(
            Map.of(
                "id", "id-value",
                "namespace", "namespace-value",
                "revision", 42
            ), variables.get("flow")
        );
    }

    @Test
    void shouldGetVariablesGivenFlowWithTenant() {
        Map<String, Object> variables = new RunVariables.DefaultBuilder()
            .withFlow(
                Flow
                    .builder()
                    .id("id-value")
                    .namespace("namespace-value")
                    .revision(42)
                    .tenantId("tenant-value")
                    .build()
            )
            .build(new RunContextLogger(), PropertyContext.create(renderer));
        Assertions.assertEquals(
            Map.of(
                "id", "id-value",
                "namespace", "namespace-value",
                "revision", 42,
                "tenantId", "tenant-value"
            ), variables.get("flow")
        );
    }

    @Test
    void shouldGetVariablesGivenTask() {
        Map<String, Object> variables = new RunVariables.DefaultBuilder()
            .withTask(new Task() {
                @Override
                public String getId() {
                    return "id-value";
                }

                @Override
                public String getType() {
                    return "type-value";
                }
            })
            .build(new RunContextLogger(), PropertyContext.create(renderer));
        Assertions.assertEquals(Map.of("id", "id-value", "type", "type-value"), variables.get("task"));
    }

    @Test
    void shouldGetVariablesGivenTrigger() {
        Map<String, Object> variables = new RunVariables.DefaultBuilder()
            .withTrigger(new AbstractTrigger() {
                @Override
                public String getId() {
                    return "id-value";
                }

                @Override
                public String getType() {
                    return "type-value";
                }
            })
            .build(new RunContextLogger(), PropertyContext.create(renderer));
        Assertions.assertEquals(Map.of("id", "id-value", "type", "type-value"), variables.get("trigger"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetKestraConfiguration() {
        Map<String, Object> variables = new RunVariables.DefaultBuilder()
            .withKestraConfiguration(new RunVariables.KestraConfiguration("test", "http://localhost:8080"))
            .build(new RunContextLogger(), PropertyContext.create(renderer));
        assertThat(variables.size()).isEqualTo(4);
        Map<String, Object> kestra = (Map<String, Object>) variables.get("kestra");
        assertThat(kestra).hasSize(2);
        assertThat(kestra.get("environment")).isEqualTo("test");
        assertThat(kestra.get("url")).isEqualTo("http://localhost:8080");
    }

    @Test
    void nonResolvableDynamicInputsShouldBeSkipped() {
        VariableRenderer.VariableConfiguration mkVariableConfiguration = Mockito.mock(VariableRenderer.VariableConfiguration.class);
        ApplicationContext mkApplicationContext = Mockito.mock(ApplicationContext.class);
        MeterRegistry mkMeterRegistry = Mockito.mock(MeterRegistry.class);
        Map<String, Object> variables = new RunVariables.DefaultBuilder()
            .withFlow(
                Flow
                    .builder()
                    .namespace("a.b")
                    .id("c")
                    .inputs(
                        List.of(
                            BoolInput.builder().id("a").type(Type.BOOL).defaults(Property.ofValue(true)).build(),
                            BoolInput.builder().id("b").type(Type.BOOL).dependsOn(new DependsOn(List.of("a"), null)).defaults(Property.ofExpression("{{inputs.a == true}}")).build()
                        )
                    )
                    .build()
            )
            .withExecution(Execution.builder().id(IdUtils.create()).build())
            .build(
                new RunContextLogger(),
                PropertyContext.create(new VariableRenderer(new PebbleEngineFactory(mkApplicationContext, mkVariableConfiguration, mkMeterRegistry), mkVariableConfiguration))
            );

        Assertions.assertEquals(
            Map.of(
                "a", true
            ), variables.get("inputs")
        );
    }

    @Test
    void shouldBuildVariablesGivenFlowWithInputHavingDefaultPebbleExpression() {
        FlowInterface flow = GenericFlow.fromYaml(TenantService.MAIN_TENANT, """
            id: id-value
            namespace: namespace-value
            inputs:
            - id: input
              type: STRING
              defaults: "{{ kv('???') }}"
            """);

        Map<String, Object> variables = new RunVariables.DefaultBuilder()
            .withFlow(flow)
            .withExecution(Execution.builder().id(IdUtils.create()).build())
            .build(new RunContextLogger(), PropertyContext.create(renderer));

        assertThat(variables.get("inputs")).isEqualTo(Map.of("input", "value"));
    }

    @Test
    void shouldBuildVariablesGivenFlowWithLabelsAndNoExecution() {
        FlowInterface flow = GenericFlow.fromYaml(TenantService.MAIN_TENANT, """
            id: opossum_534817
            namespace: company.team

            labels:
              some: label

            triggers:
              - id: schedule
                type: io.kestra.plugin.core.trigger.Schedule
                cron: "* * * * *"
                inputs:
                  fromLabel: "{{labels.some}}"

            tasks:
              - id: hello
                type: io.kestra.plugin.core.log.Log
                message: Hello World! 🚀
            """);

        Map<String, Object> variables = new RunVariables.DefaultBuilder()
            .withFlow(flow)
            .build(new RunContextLogger(), PropertyContext.create(renderer));

        assertThat(variables.get("labels")).isEqualTo(Map.of("some", "label"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDecryptSecretInputFromEncryptedMap() throws GeneralSecurityException {
        // Given
        String secretKey = "I6EGNzRESu3X3pKZidrqCGOHQFUFC0yK";
        String plaintext = "my-secret-value";
        String encrypted = EncryptionService.encrypt(secretKey, plaintext);

        Flow flow = Flow.builder()
            .id("test-flow")
            .namespace("test")
            .inputs(List.of(
                SecretInput.builder().id("mySecret").type(Type.SECRET).build()
            ))
            .build();

        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .inputs(Map.of("mySecret", Map.of("type", EncryptedString.TYPE, "value", encrypted)))
            .build();

        // When
        Map<String, Object> variables = new RunVariables.DefaultBuilder(Optional.of(secretKey))
            .withFlow(flow)
            .withExecution(execution)
            .build(new RunContextLogger(), PropertyContext.create(renderer));

        // Then
        Map<String, Object> inputs = (Map<String, Object>) variables.get("inputs");
        assertThat(inputs.get("mySecret")).isEqualTo(plaintext);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotFailWhenSecretInputIsAlreadyDecryptedString() {
        // Given - simulates the subflow execution end scenario where the input is already a plain String
        String secretKey = "I6EGNzRESu3X3pKZidrqCGOHQFUFC0yK";

        Flow flow = Flow.builder()
            .id("test-flow")
            .namespace("test")
            .inputs(List.of(
                SecretInput.builder().id("mySecret").type(Type.SECRET).build()
            ))
            .build();

        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .inputs(Map.of("mySecret", "already-decrypted-value"))
            .build();

        // When
        Map<String, Object> variables = new RunVariables.DefaultBuilder(Optional.of(secretKey))
            .withFlow(flow)
            .withExecution(execution)
            .build(new RunContextLogger(), PropertyContext.create(renderer));

        // Then - should not throw ClassCastException, value remains as-is
        Map<String, Object> inputs = (Map<String, Object>) variables.get("inputs");
        assertThat(inputs.get("mySecret")).isEqualTo("already-decrypted-value");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleNestedSecretInputWithNonMapValue() {
        // Given - nested input id with '.' where the nested value is not a Map
        String secretKey = "I6EGNzRESu3X3pKZidrqCGOHQFUFC0yK";

        Flow flow = Flow.builder()
            .id("test-flow")
            .namespace("test")
            .inputs(List.of(
                SecretInput.builder().id("parent.mySecret").type(Type.SECRET).build()
            ))
            .build();

        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .inputs(Map.of("parent", "not-a-map"))
            .build();

        // When
        Map<String, Object> variables = new RunVariables.DefaultBuilder(Optional.of(secretKey))
            .withFlow(flow)
            .withExecution(execution)
            .build(new RunContextLogger(), PropertyContext.create(renderer));

        // Then - should not throw ClassCastException
        Map<String, Object> inputs = (Map<String, Object>) variables.get("inputs");
        assertThat(inputs.get("parent")).isEqualTo("not-a-map");
    }
}
