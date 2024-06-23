package io.kestra.core.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class TaskGlobalDefaultConfigurationTest {
    @SuppressWarnings("unchecked")
    @Test
    public void keepCase() {
        // a classic UNIX variable: AB_VALUE='test'
        Map<String, Object> env = Map.of("AB_VALUE", "test", "ABCVALUE", "test");

        Map<String, Object> values = Map.of("env", env);
        Map<String, Object> task = Map.of(
            "type", "io.kestra.plugin.scripts.shell.Commands",
            "values", values
        );
        List<Object> defaultTasks = List.of(task);
        Map<String, Object> defaults = Map.of("defaults", defaultTasks);
        Map<String, Object> tasks = Map.of("tasks", defaults);
        Map<String, Object> kestra = Map.of("kestra", tasks);


        try (ApplicationContext ctx = ApplicationContext.run(kestra, Environment.CLI, Environment.TEST)) {
            TaskGlobalDefaultConfiguration taskDefaultGlobalConfiguration = ctx.getBean(TaskGlobalDefaultConfiguration.class);

            assertThat(
                ((Map<String, String>) taskDefaultGlobalConfiguration.getDefaults()
                    .getFirst()
                    .getValues()
                    .get("env")).keySet(),
                is(Set.of("AB_VALUE", "ABCVALUE"))
            );
        }
    }
}