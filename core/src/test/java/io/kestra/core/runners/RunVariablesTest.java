package io.kestra.core.runners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LoopRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.DependsOn;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.BoolInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.property.PropertyContext;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.configuration.VariableConfiguration;
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

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class RunVariablesTest {

    @Inject
    VariableRenderer renderer;

    @Inject
    StorageInterface storageInterface;

    @Inject
    KVMetadataStateStore kvMetadataStateStore;

    @MockBean(KVStoreService.class)
    KVStoreService testKVStoreService() {
        return new KVStoreService() {
            @Override
            public KVStore get(String tenant, String namespace, @Nullable String fromNamespace) {
                return new InternalKVStore(tenant, namespace, storageInterface, kvMetadataStateStore) {
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
        VariableConfiguration mkVariableConfiguration = Mockito.mock(VariableConfiguration.class);
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
    void allContextPathsShouldContainExpectedStructuralPaths() {
        List<String> paths = RunVariables.allContextPaths();

        assertThat(paths).isNotEmpty();

        // Core structural paths that every Kestra user depends on
        assertThat(paths).contains(
            "flow.id",
            "flow.namespace",
            "execution.id",
            "execution.startDate",
            "execution.state",
            "taskrun.id",
            "taskrun.value",
            "item.index",
            "task.id",
            "kestra.environment",
            "kestra.url"
        );

        // Dynamic top-level keys — present once, no nested children in the registry
        assertThat(paths).contains("inputs", "outputs", "labels");
        assertThat(paths).noneMatch(p -> p.startsWith("inputs."));
        assertThat(paths).noneMatch(p -> p.startsWith("outputs."));
        assertThat(paths).noneMatch(p -> p.startsWith("labels."));
    }

    /**
     * Drift-detection test: every structural path produced by {@link RunVariables.DefaultBuilder#build}
     * must be declared in {@link RunVariables#EXECUTION_CONTEXT_PATHS}.
     * <p>
     * If {@code DefaultBuilder.build()} adds a new top-level key or nested structural field
     * that is not in {@code EXECUTION_CONTEXT_PATHS}, this test will fail — forcing the
     * developer to update the explicit list.
     * <p>
     * Dynamic top-level keys ({@code inputs}, {@code outputs}, {@code tasks}, etc.) are noted
     * as present but their children are not walked, since their structure varies per flow/execution.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void contextPathsShouldMatchExplicitRegistry() {
        String parentRunId = IdUtils.create();
        String childRunId = IdUtils.create();

        // TaskRun stubs with all optional fields populated (parentId, value, iteration)
        TaskRun parentRun = TaskRun.builder()
            .id(parentRunId).taskId("parent-task").executionId("exec-id")
            .namespace("ns").flowId("flow").value("each-value").state(new State()).build();
        TaskRun childRun = TaskRun.builder()
            .id(childRunId).taskId("child-task").executionId("exec-id")
            .namespace("ns").flowId("flow")
            .parentTaskRunId(parentRunId).value("item-value").iteration(2)
            .state(new State()).build();

        // LoopRun with key set and two parents (last has a non-null key → item.parent.key appears)
        Execution parentExecution = Execution.builder()
            .id("parent-exec-id").namespace("ns").flowId("flow").state(new State())
            .outputs(Map.of())
            .build()
            .withState(State.Type.SUCCESS);
        LoopRun loopRun = new LoopRun(
            parentExecution, "loop-task", IdUtils.create(), 0, "loop-key", "loop-value",
            List.of(new LoopRun.Parent(0, null, "v0"), new LoopRun.Parent(1, "pk", "v1"))
        );

        Execution execution = Execution.builder()
            .id("exec-id").namespace("ns").flowId("flow").state(new State())
            .taskRunList(List.of(parentRun, childRun))
            .labels(List.of(new Label("env", "prod")))
            .loopRun(loopRun)
            .variables(new java.util.HashMap<>(Map.of(RunVariables.FIXTURE_FILES_KEY, Map.of())))
            .outputs(Map.of())
            .build();

        Map<String, Object> variables = new RunVariables.DefaultBuilder()
            .withFlow(GenericFlow.builder().id("flow").namespace("ns").revision(1).tenantId("tenant").build())
            .withTask(new Task() {
                @Override public String getId() { return "task-id"; }
                @Override public String getType() { return "task-type"; }
            })
            .withTaskRun(childRun)
            .withExecution(execution)
            .withEnvs(Map.of("MY_ENV", "value"))
            .withGlobals(Map.of("myGlobal", "value"))
            .withInputs(Map.of("myInput", "value"))
            .withKestraConfiguration(new RunVariables.KestraConfiguration("test", "http://localhost"))
            .build(new RunContextLogger(), PropertyContext.create(renderer));

        // Dynamic top-level keys whose children vary per flow/execution — not walked
        Set<String> dynamicTopLevel = Set.of("envs", "files", "globals", "inputs", "labels",
            "outputs", "tasks", "vars", RunVariables.SECRET_CONSUMER_VARIABLE_NAME);

        List<String> foundPaths = new ArrayList<>();
        collectStructuralPaths(variables, "", dynamicTopLevel, foundPaths);

        // Forward direction: every structural path produced must be declared in EXECUTION_CONTEXT_PATHS
        for (String path : foundPaths) {
            assertThat(RunVariables.EXECUTION_CONTEXT_PATHS)
                .as("Path '%s' produced by DefaultBuilder.build() is not in EXECUTION_CONTEXT_PATHS — update the list", path)
                .contains(path);
        }

        // Reverse direction: every static entry in EXECUTION_CONTEXT_PATHS must be producible.
        // Dynamic sub-key prefixes (envs.*, globals.*, labels.*) are excluded since they vary per flow/execution.
        Set<String> dynamicSubPrefixes = Set.of("envs.", "globals.", "labels.");
        for (String registeredPath : RunVariables.EXECUTION_CONTEXT_PATHS) {
            boolean isDynamicSub = dynamicSubPrefixes.stream().anyMatch(registeredPath::startsWith);
            if (!isDynamicSub) {
                assertThat(foundPaths)
                    .as("Path '%s' in EXECUTION_CONTEXT_PATHS is never produced by DefaultBuilder.build() — remove it or update the test setup", registeredPath)
                    .contains(registeredPath);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectStructuralPaths(Map<String, ?> map, String prefix, Set<String> stopAt, List<String> paths) {
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            String key = entry.getKey();
            // Skip the secret consumer lambda
            if (RunVariables.SECRET_CONSUMER_VARIABLE_NAME.equals(key) && prefix.isEmpty()) {
                continue;
            }
            if (stopAt.contains(key) && prefix.isEmpty()) {
                // Dynamic key: record the top-level key but don't walk flow-specific children
                paths.add(key);
                continue;
            }
            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;
            paths.add(fullPath);
            if (entry.getValue() instanceof Map<?, ?> nested) {
                collectStructuralPaths((Map<String, ?>) nested, fullPath, stopAt, paths);
            }
            // Lists (e.g. parents) — record the path but don't recurse into list elements
        }
    }
}
