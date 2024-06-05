package io.kestra.core.runners;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class RunVariablesTest {

    @Test
    void shouldGetEmptyVariables() {
        Map<String, Object> variables = new RunVariables.DefaultBuilder().build(new RunContextLogger());
        Assertions.assertEquals(Map.of("envs", Map.of(), "globals", Map.of()), variables);
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
            .build(new RunContextLogger());
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
            .build(new RunContextLogger());
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
            .build(new RunContextLogger());
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
            .build(new RunContextLogger());
        Assertions.assertEquals(Map.of("id", "id-value", "type", "type-value"), variables.get("trigger"));
    }
}