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
 * type-matched plugin-default injection.
 *
 * <p>Each invocation re-parses the flow source (two Jackson passes bracket the injection), so the
 * input flows are safely shared at trial level. The {@code noDefaults} scenario isolates the
 * parse + traversal cost, and {@code typeDefaults} adds the cost of matching and merging
 * type-matched defaults onto every task.
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

    private PluginDefaultService pluginDefaultService;

    private GenericFlow noDefaults;
    private GenericFlow typeDefaults;

    @Setup(Level.Trial)
    public void setup() {
        pluginDefaultService = new PluginDefaultService(null, null, DefaultPluginRegistry.getOrCreate());

        noDefaults = GenericFlow.fromYaml("main", flowSource(""));
        typeDefaults = GenericFlow.fromYaml("main", flowSource(typeDefaultEntries()));
    }

    /**
     * Baseline: parse + traversal cost with no defaults to inject.
     */
    @Benchmark
    public FlowWithSource injectNoDefaults() throws Exception {
        return pluginDefaultService.injectAllDefaults(noDefaults, false);
    }

    /**
     * Type-matched defaults on every task.
     */
    @Benchmark
    public FlowWithSource injectTypeDefaults() throws Exception {
        return pluginDefaultService.injectAllDefaults(typeDefaults, false);
    }

    /**
     * Builds a flow with {@link #TASK_COUNT} tasks, half Log and half Return.
     */
    private static String flowSource(String pluginDefaultEntries) {
        StringBuilder sb = new StringBuilder();
        sb.append("id: plugin-default-benchmark\n");
        sb.append("namespace: io.kestra.benchmark\n");
        if (!pluginDefaultEntries.isEmpty()) {
            sb.append("pluginDefaults:\n").append(pluginDefaultEntries);
        }
        sb.append("tasks:\n");

        for (int i = 0; i < TASK_COUNT / 2; i++) {
            appendTask(sb, "log-task-" + i, LOG_TYPE, "message: hello " + i);
            appendTask(sb, "return-task-" + i, RETURN_TYPE, "format: value " + i);
        }

        return sb.toString();
    }

    private static void appendTask(StringBuilder sb, String id, String type, String property) {
        sb.append("  - id: ").append(id).append('\n');
        sb.append("    type: ").append(type).append('\n');
        sb.append("    ").append(property).append('\n');
    }

    /** {@link #DEFAULTS_PER_TYPE} type-matched defaults per task type. */
    private static String typeDefaultEntries() {
        StringBuilder sb = new StringBuilder();
        appendDefaultEntries(sb, LOG_TYPE);
        appendDefaultEntries(sb, RETURN_TYPE);
        return sb.toString();
    }

    // note: no 'forced' entries — the flag is ignored (and warned about) at flow level
    private static void appendDefaultEntries(StringBuilder sb, String type) {
        for (int i = 0; i < DEFAULTS_PER_TYPE; i++) {
            sb.append("  - type: ").append(type).append('\n');
            sb.append("    values:\n");
            sb.append("      logLevel: DEBUG\n");
            sb.append("      description: type-default-").append(i).append('\n');
        }
    }
}
