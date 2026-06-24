package io.kestra.core.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.plugins.PluginRegistry;
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

    @Inject
    private PluginRegistry pluginRegistry;

    /**
     * Tests flow-vs-global default precedence across the forced/non-forced matrix:
     * <ol>
     *   <li>Non-forced defaults follow author-first precedence: explicit task values win, then flow
     *       defaults, then global defaults.</li>
     *   <li>A flow forced default is honored on this branch and overrides explicit task values, but
     *       only when no global forced default sets the same property.</li>
     *   <li>Forced defaults follow admin-first precedence: a global (admin) forced default wins over a
     *       flow forced default and over explicit task values (kestra-ee#8262) — the reverse of the
     *       non-forced order.</li>
     * </ol>
     */
    @org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
    @ParameterizedTest
    @MethodSource
    void flowDefaultsOverrideGlobalDefaults(boolean flowDefaultForced, boolean globalDefaultForced, String fooValue, String barValue, String bazValue) throws FlowProcessingException {
        final DefaultPrecedenceTester task = DefaultPrecedenceTester.builder()
            .id("test")
            .type(DefaultPrecedenceTester.class.getName())
            .propBaz("taskValue")
            .build();

        final PluginDefault flowDefault = new PluginDefault(
            DefaultPrecedenceTester.class.getName(), flowDefaultForced, ImmutableMap.of(
                "propBar", "flowValue",
                "propBaz", "flowValue"
            )
        );
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

    /**
     * Forced defaults follow admin-first precedence: when both a namespace-level (EE) and a global
     * (configuration) forced default match the same plugin, the global one wins (kestra-ee#8262) —
     * the reverse of non-forced precedence (flow beats namespace beats global). The namespace level
     * does not exist in OSS, so it is simulated by overriding {@code getAllDefaults}, which is exactly
     * the seam the EE service uses to contribute namespace defaults.
     */
    @Test
    void globalForcedDefaultBeatsNamespaceForcedDefault() throws FlowProcessingException {
        final DefaultPrecedenceTester task = DefaultPrecedenceTester.builder()
            .id("test")
            .type(DefaultPrecedenceTester.class.getName())
            .propFoo("taskValue")
            .build();

        final PluginDefault namespaceForced = new PluginDefault(
            DefaultPrecedenceTester.class.getName(), true, ImmutableMap.of(
                "propFoo", "namespaceValue",
                "propBar", "namespaceValue"
            )
        );
        final PluginDefault globalForced = new PluginDefault(
            DefaultPrecedenceTester.class.getName(), true, ImmutableMap.of("propFoo", "globalValue")
        );

        // defaults are ordered most-important-first: flow, then namespace, then global
        final PluginDefaultService service = new PluginDefaultService() {
            @Override
            protected List<PluginDefault> getAllDefaults(String tenantId, String namespace, Map<String, Object> flow) {
                List<PluginDefault> defaults = new ArrayList<>(getFlowDefaults(flow));
                defaults.add(namespaceForced);
                defaults.add(globalForced);
                return defaults;
            }
        };
        service.pluginRegistry = pluginRegistry;

        var tenant = TestsUtils.randomTenant(PluginDefaultServiceOverrideTest.class.getSimpleName());
        final Flow flow = Flow.builder()
            .tenantId(tenant)
            .tasks(Collections.singletonList(task))
            .build();

        final Flow injected = service.injectAllDefaults(flow, true);

        DefaultPrecedenceTester result = (DefaultPrecedenceTester) injected.getTasks().getFirst();
        // the global (admin) forced default wins over the namespace forced one
        assertThat(result.getPropFoo(), is("globalValue"));
        // non-overlapping namespace forced values still apply
        assertThat(result.getPropBar(), is("namespaceValue"));
    }

    /**
     * When a {@code forced} (admin-enforced) type default matches a plugin that also declares a
     * {@code pluginDefaultsRef}, enforcement takes over entirely: the referenced plugin-defaults is ignored —
     * not stacked. The forced value wins on the enforced property AND the ref's other (non-overlapping)
     * properties are dropped.
     */
    @org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
    @Test
    void forcedTypeDefaultBeatsForcedRef() throws FlowProcessingException {
        final DefaultPrecedenceTester task = DefaultPrecedenceTester.builder()
            .id("test")
            .type(DefaultPrecedenceTester.class.getName())
            .pluginDefaultsRef("bypass")
            .propFoo("taskValue")
            .build();

        // a forced ref cannot bypass a forced TYPE default: even though flow-level 'forced' is honored on this
        // release line, the forced type default still takes over entirely and the ref is ignored (no stacking)
        final PluginDefault flowForcedRef = PluginDefault.builder()
            .type(DefaultPrecedenceTester.class.getName())
            .ref("bypass")
            .forced(true)
            .values(ImmutableMap.of("propFoo", "refValue", "propBar", "refBar"))
            .build();

        final PluginDefault globalForcedType = new PluginDefault(
            DefaultPrecedenceTester.class.getName(), true, ImmutableMap.of("propFoo", "globalValue")
        );

        var tenant = TestsUtils.randomTenant(PluginDefaultServiceOverrideTest.class.getSimpleName());
        final Flow flow = Flow.builder()
            .tenantId(tenant)
            .tasks(Collections.singletonList(task))
            .pluginDefaults(List.of(flowForcedRef))
            .build();

        final PluginGlobalDefaultConfiguration config = new PluginGlobalDefaultConfiguration();
        config.defaults = List.of(globalForcedType);

        var previous = pluginDefaultService.pluginGlobalDefault;
        pluginDefaultService.pluginGlobalDefault = config;
        final Flow injected = pluginDefaultService.injectAllDefaults(flow, true);
        pluginDefaultService.pluginGlobalDefault = previous;

        DefaultPrecedenceTester result = (DefaultPrecedenceTester) injected.getTasks().getFirst();
        // forced TYPE default wins on the enforced property...
        assertThat(result.getPropFoo(), is("globalValue"));
        // ...and the ref is ignored entirely — its non-overlapping property does NOT stack on top
        assertThat(result.getPropBar(), is((String) null));
    }

    private static Stream<Arguments> flowDefaultsOverrideGlobalDefaults() {
        return Stream.of(
            Arguments.of(false, false, "globalValue", "flowValue", "taskValue"),
            Arguments.of(false, true, "globalValue", "globalValue", "globalValue"),
            Arguments.of(true, false, "globalValue", "flowValue", "flowValue"),
            Arguments.of(true, true, "globalValue", "globalValue", "globalValue")
        );
    }
}