package io.kestra.core.services;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowPluginDefault;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.services.PluginDefaultServiceTest.DefaultPrecedenceTester;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@KestraTest
class PluginDefaultServiceOverrideTest {
    @Inject
    private PluginDefaultService pluginDefaultService;

    /**
     * Tests that:
     * 1. Flow-level plugin defaults (which can never be forced) apply values not set by the task.
     * 2. Global forced defaults override both flow defaults and explicit task values.
     * 3. Global non-forced defaults are overridden by explicit task values and flow defaults.
     *
     * <p>The {@code forced} flag at flow level was removed (kestra-ee#7109). All flow-level defaults
     * behave as non-forced regardless of the original YAML value.</p>
     */
    @org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
    @ParameterizedTest
    @MethodSource
    void flowDefaultsOverrideGlobalDefaults(boolean globalDefaultForced, String fooValue, String barValue, String bazValue) throws FlowProcessingException {
        final DefaultPrecedenceTester task = DefaultPrecedenceTester.builder()
            .id("test")
            .type(DefaultPrecedenceTester.class.getName())
            .propBaz("taskValue")
            .build();

        // Flow-level defaults have no 'forced' flag — always non-forced
        final FlowPluginDefault flowDefault = FlowPluginDefault.builder()
            .type(DefaultPrecedenceTester.class.getName())
            .values(ImmutableMap.of(
                "propBar", "flowValue",
                "propBaz", "flowValue"
            ))
            .build();

        final PluginDefault globalDefault = new PluginDefault(
            DefaultPrecedenceTester.class.getName(), globalDefaultForced, ImmutableMap.of(
                "propFoo", "globalValue",
                "propBar", "globalValue",
                "propBaz", "globalValue"
            )
        );

        var tenant = TestsUtils.randomTenant(PluginDefaultServiceOverrideTest.class.getSimpleName());
        final Flow flowWithPluginDefault = Flow.builder()
            .tenantId(tenant)
            .tasks(Collections.singletonList(task))
            .pluginDefaults(List.of(flowDefault))
            .build();

        final PluginGlobalDefaultConfiguration pluginGlobalDefaultConfiguration = new PluginGlobalDefaultConfiguration();
        pluginGlobalDefaultConfiguration.defaults = List.of(globalDefault);

        var previousGlobalDefault = pluginDefaultService.pluginGlobalDefault;
        pluginDefaultService.pluginGlobalDefault = pluginGlobalDefaultConfiguration;

        final Flow injected = pluginDefaultService.injectAllDefaults(flowWithPluginDefault, true);
        pluginDefaultService.pluginGlobalDefault = previousGlobalDefault;

        assertThat(((DefaultPrecedenceTester) injected.getTasks().getFirst()).getPropFoo(), is(fooValue));
        assertThat(((DefaultPrecedenceTester) injected.getTasks().getFirst()).getPropBar(), is(barValue));
        assertThat(((DefaultPrecedenceTester) injected.getTasks().getFirst()).getPropBaz(), is(bazValue));
    }

    private static Stream<Arguments> flowDefaultsOverrideGlobalDefaults() {
        return Stream.of(
            // Non-forced global: task wins for propBaz, flow default wins for propBar, global fills propFoo
            Arguments.of(false, "globalValue", "flowValue", "taskValue"),
            // Forced global: global overrides everything including task value
            Arguments.of(true, "globalValue", "globalValue", "globalValue")
        );
    }
}
