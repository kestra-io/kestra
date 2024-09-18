package io.kestra.core.runners;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class RunVariablesTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetEmptyVariables() {
        Map<String, Object> variables = new RunVariables.DefaultBuilder().build(new RunContextLogger());
        assertThat(variables.size(), is(3));
        assertThat((Map<String, Object>) variables.get("envs"), is(Map.of()));
        assertThat((Map<String, Object>) variables.get("globals"), is(Map.of()));
        assertThat(variables.get("addSecretConsumer"), notNullValue());
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