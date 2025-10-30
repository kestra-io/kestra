package io.kestra.core.services;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.services.PluginDefaultServiceTest.DefaultPrecedenceTester;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@KestraTest
class PluginDefaultServiceOverrideTest {
    @Inject
    private PluginDefaultService pluginDefaultService;

    @org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
    @ParameterizedTest
    @MethodSource
    void flowDefaultsOverrideGlobalDefaults(boolean flowDefaultForced, boolean globalDefaultForced, String fooValue, String barValue, String bazValue) throws FlowProcessingException {
        final DefaultPrecedenceTester task = DefaultPrecedenceTester.builder()
            .id("test")
            .type(DefaultPrecedenceTester.class.getName())
            .propBaz("taskValue")
            .build();

        final PluginDefault flowDefault = new PluginDefault(DefaultPrecedenceTester.class.getName(), flowDefaultForced, ImmutableMap.of(
            "propBar", "flowValue",
            "propBaz", "flowValue"
        ));
        final PluginDefault globalDefault = new PluginDefault(DefaultPrecedenceTester.class.getName(), globalDefaultForced, ImmutableMap.of(
            "propFoo", "globalValue",
            "propBar", "globalValue",
            "propBaz", "globalValue"
        ));

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
            Arguments.of(false, false, "globalValue", "flowValue", "taskValue"),
            Arguments.of(false, true, "globalValue", "globalValue", "globalValue"),
            Arguments.of(true, false, "globalValue", "flowValue", "flowValue"),
            Arguments.of(true, true, "globalValue", "flowValue", "flowValue")
        );
    }
}