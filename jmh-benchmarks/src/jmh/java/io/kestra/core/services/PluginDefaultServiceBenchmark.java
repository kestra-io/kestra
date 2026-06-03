package io.kestra.core.services;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.plugins.DefaultPluginRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks {@link PluginDefaultService#injectAllDefaults} on large flows to track the cost of
 * plugin-default injection (type-matched and named/ref defaults).
 *
 * <p>Each invocation re-parses the flow source (two Jackson passes bracket the injection), so the
 * input flows are safely shared at trial level. The {@code noDefaults} and {@code typeDefaults}
 * scenarios are directly comparable against earlier revisions; the {@code refDefaults} and
 * {@code mixedDefaults} scenarios characterize the named-defaults feature introduced in KESTRA#16446.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
public class PluginDefaultServiceBenchmark {
    private static final int TASK_COUNT = 100;
    private static final int DEFAULTS_PER_TYPE = 10;
    private static final String LOG_TYPE = "io.kestra.plugin.core.log.Log";
    private static final String RETURN_TYPE = "io.kestra.plugin.core.debug.Return";

    /** Which tasks opt into a named bundle via {@code pluginDefaultsRef}. */
    private enum RefAssignment {
        NONE,
        EVERY_TASK,
        /** Log tasks use ref bundles, Return tasks fall back to type-matched defaults. */
        LOG_TASKS_ONLY
    }

    private PluginDefaultService pluginDefaultService;

    private GenericFlow noDefaults;
    private GenericFlow typeDefaults;
    private GenericFlow refDefaults;
    private GenericFlow mixedDefaults;

    @Setup(Level.Trial)
    public void setup() {
        pluginDefaultService = new PluginDefaultService(null, null, DefaultPluginRegistry.getOrCreate());

        noDefaults = GenericFlow.fromYaml("main", flowSource(RefAssignment.NONE, ""));
        typeDefaults = GenericFlow.fromYaml("main", flowSource(RefAssignment.NONE, typeDefaultEntries()));
        refDefaults = GenericFlow.fromYaml("main", flowSource(RefAssignment.EVERY_TASK, refDefaultEntries()));
        mixedDefaults = GenericFlow.fromYaml("main", flowSource(RefAssignment.LOG_TASKS_ONLY, typeDefaultEntries() + refDefaultEntries()));
    }

    /**
     * Baseline: parse + traversal cost with no defaults to inject.
     *
     * @see <a href="https://github.com/kestra-io/kestra/pull/16446">KESTRA#16446</a>
     */
    @Benchmark
    public FlowWithSource injectNoDefaults() throws Exception {
        return pluginDefaultService.injectAllDefaults(noDefaults, false);
    }

    /**
     * Type-matched defaults on every task.
     *
     * @see <a href="https://github.com/kestra-io/kestra/pull/16446">KESTRA#16446</a>
     */
    @Benchmark
    public FlowWithSource injectTypeDefaults() throws Exception {
        return pluginDefaultService.injectAllDefaults(typeDefaults, false);
    }

    /**
     * Named (ref) defaults: every task opts into a bundle via {@code pluginDefaultsRef}.
     *
     * @see <a href="https://github.com/kestra-io/kestra/pull/16446">KESTRA#16446</a>
     */
    @Benchmark
    public FlowWithSource injectRefDefaults() throws Exception {
        return pluginDefaultService.injectAllDefaults(refDefaults, false);
    }

    /**
     * Mixed type-matched and named (ref) defaults: half the tasks opt into a ref bundle, the other
     * half receive type-matched defaults.
     *
     * @see <a href="https://github.com/kestra-io/kestra/pull/16446">KESTRA#16446</a>
     */
    @Benchmark
    public FlowWithSource injectMixedDefaults() throws Exception {
        return pluginDefaultService.injectAllDefaults(mixedDefaults, false);
    }

    /**
     * Builds a flow with {@link #TASK_COUNT} tasks, half Log and half Return. Tasks selected by
     * {@code refAssignment} reference one of the {@link #DEFAULTS_PER_TYPE} named bundles declared
     * for their own type ({@code log-ref-*} for Log tasks, {@code return-ref-*} for Return tasks).
     */
    private static String flowSource(RefAssignment refAssignment, String pluginDefaultEntries) {
        StringBuilder sb = new StringBuilder();
        sb.append("id: plugin-default-benchmark\n");
        sb.append("namespace: io.kestra.benchmark\n");
        if (!pluginDefaultEntries.isEmpty()) {
            sb.append("pluginDefaults:\n").append(pluginDefaultEntries);
        }
        sb.append("tasks:\n");

        for (int i = 0; i < TASK_COUNT / 2; i++) {
            int bundle = i % DEFAULTS_PER_TYPE; // cycle through the declared bundle ids
            appendTask(sb, "log-task-" + i, LOG_TYPE, "message: hello " + i,
                refAssignment == RefAssignment.NONE ? null : "log-ref-" + bundle);
            appendTask(sb, "return-task-" + i, RETURN_TYPE, "format: value " + i,
                refAssignment == RefAssignment.EVERY_TASK ? "return-ref-" + bundle : null);
        }

        return sb.toString();
    }

    private static void appendTask(StringBuilder sb, String id, String type, String property, String pluginDefaultsRef) {
        sb.append("  - id: ").append(id).append('\n');
        sb.append("    type: ").append(type).append('\n');
        sb.append("    ").append(property).append('\n');
        if (pluginDefaultsRef != null) {
            sb.append("    pluginDefaultsRef: ").append(pluginDefaultsRef).append('\n');
        }
    }

    /** {@link #DEFAULTS_PER_TYPE} type-matched defaults per task type. */
    private static String typeDefaultEntries() {
        StringBuilder sb = new StringBuilder();
        appendDefaultEntries(sb, LOG_TYPE, null);
        appendDefaultEntries(sb, RETURN_TYPE, null);
        return sb.toString();
    }

    /** {@link #DEFAULTS_PER_TYPE} named (ref) defaults per task type. */
    private static String refDefaultEntries() {
        StringBuilder sb = new StringBuilder();
        appendDefaultEntries(sb, LOG_TYPE, "log-ref-");
        appendDefaultEntries(sb, RETURN_TYPE, "return-ref-");
        return sb.toString();
    }

    // note: no 'forced' entries — the flag is ignored (and warned about) at flow level
    private static void appendDefaultEntries(StringBuilder sb, String type, String refPrefix) {
        boolean named = refPrefix != null;
        for (int i = 0; i < DEFAULTS_PER_TYPE; i++) {
            sb.append("  - type: ").append(type).append('\n');
            if (named) {
                sb.append("    ref: ").append(refPrefix).append(i).append('\n');
            }
            sb.append("    values:\n");
            sb.append("      logLevel: ").append(named ? "TRACE" : "DEBUG").append('\n');
            sb.append("      description: ").append(named ? "ref" : "type").append("-default-").append(i).append('\n');
        }
    }
}
