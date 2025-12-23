package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.DependsOn;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.BoolInput;
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
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            .withFlow(Flow
                .builder()
                .id("id-value")
                .namespace("namespace-value")
                .revision(42)
                .build()
            )
            .build(new RunContextLogger(), PropertyContext.create(renderer));
        Assertions.assertEquals(Map.of(
            "id", "id-value",
            "namespace", "namespace-value",
            "revision", 42
        ), variables.get("flow"));
    }

    @Test
    void shouldGetVariablesGivenFlowWithTenant() {
        Map<String, Object> variables = new RunVariables.DefaultBuilder()
            .withFlow(Flow
                .builder()
                .id("id-value")
                .namespace("namespace-value")
                .revision(42)
                .tenantId("tenant-value")
                .build()
            )
            .build(new RunContextLogger(), PropertyContext.create(renderer));
        Assertions.assertEquals(Map.of(
            "id", "id-value",
            "namespace", "namespace-value",
            "revision", 42,
            "tenantId", "tenant-value"
        ), variables.get("flow"));
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
            .withFlow(Flow
                .builder()
                .namespace("a.b")
                .id("c")
                .inputs(List.of(
                    BoolInput.builder().id("a").type(Type.BOOL).defaults(Property.ofValue(true)).build(),
                    BoolInput.builder().id("b").type(Type.BOOL).dependsOn(new DependsOn(List.of("a"), null)).defaults(Property.ofExpression("{{inputs.a == true}}")).build()
                ))
                .build()
            )
            .withExecution(Execution.builder().id(IdUtils.create()).build())
            .build(new RunContextLogger(), PropertyContext.create(new VariableRenderer(new PebbleEngineFactory(mkApplicationContext, mkVariableConfiguration, mkMeterRegistry), mkVariableConfiguration)));

        Assertions.assertEquals(Map.of(
            "a", true
        ), variables.get("inputs"));
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
}
