package io.kestra.core.runners;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.micronaut.context.annotation.Property;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunVariablesTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetEmptyVariables() {
        Map<String, Object> variables = new RunVariables.DefaultBuilder().build(new RunContextLogger());
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

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetKestraConfiguration() {
        Map<String, Object> variables = new RunVariables.DefaultBuilder()
            .withKestraConfiguration(new RunVariables.KestraConfiguration("test", "http://localhost:8080"))
            .build(new RunContextLogger());
        assertThat(variables.size()).isEqualTo(4);
        Map<String, Object> kestra = (Map<String, Object>) variables.get("kestra");
        assertThat(kestra).hasSize(2);
        assertThat(kestra.get("environment")).isEqualTo("test");
        assertThat(kestra.get("url")).isEqualTo("http://localhost:8080");
    }
}