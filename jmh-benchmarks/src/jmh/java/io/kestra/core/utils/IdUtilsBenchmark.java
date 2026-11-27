package io.kestra.core.utils;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

/**
 * Compares the {@code StringJoiner} behind {@code IdUtils.fromParts} with a {@code StringBuilder} seeded with the
 * first part, on the two-part key the executor used to build for every task run.
 *
 * <p>
 * Result (JDK 25, 2 forks, 3 warmup iterations of 2 s and 5 measured iterations of 3 s, {@code -prof gc}): the joiner sizes the result
 * exactly and wins, so it was kept; the builder's extra bytes are the resize its ~50-character key forces.
 * <pre>
 *                  StringJoiner          StringBuilder
 * fromParts        16.1 ns, 168 B/op     24.6 ns, 320 B/op
 * </pre>
 *
 * @see <a href="https://github.com/kestra-io/kestra/pull/22484">KESTRA#22484</a>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class IdUtilsBenchmark {
    private String taskId;
    private String value;

    @Setup
    public void setup() {
        taskId = "my-task-" + IdUtils.create();
        value = IdUtils.create();
    }

    @Benchmark
    public String stringJoiner() {
        return IdUtils.fromParts(taskId, value);
    }

    @Benchmark
    public String stringBuilder() {
        return builderFromParts('_', taskId, value);
    }

    private static String builderFromParts(char separator, String... parts) {
        StringBuilder builder = null;
        for (String str : parts) {
            if (str == null) {
                continue;
            }
            if (builder == null) {
                builder = new StringBuilder(str);
            } else {
                builder.append(separator).append(str);
            }
        }
        return builder == null ? "" : builder.toString();
    }
}
