package io.kestra.core.models.flows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.openjdk.jmh.annotations.*;

/**
 * Compares commons-lang3's {@code formatDurationHMS}, which tokenizes its format string on every call, with the
 * hand-rolled formatter behind {@code State.humanDuration}, on a terminated state.
 *
 * <p>
 * Result (JDK 25, 1 fork, 3 warmup iterations of 2 s and 5 measured iterations of 3 s, {@code -prof gc}):
 * <pre>
 *                  commons-lang3         hand-rolled
 * humanDuration    184 ns, 1472 B/op     33 ns, 160 B/op
 * </pre>
 *
 * @see <a href="https://github.com/kestra-io/kestra/pull/22484">KESTRA#22484</a>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@org.openjdk.jmh.annotations.State(Scope.Benchmark)
public class StateHumanDurationBenchmark {
    private State state;

    @Setup
    public void setup() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        state = new State(State.Type.SUCCESS, List.of(
            new State.History(State.Type.CREATED, start),
            new State.History(State.Type.RUNNING, start.plusMillis(120)),
            new State.History(State.Type.SUCCESS, start.plus(Duration.ofMinutes(2).plusSeconds(3).plusMillis(45)))
        ));
    }

    @Benchmark
    public String oldHumanDuration() {
        return DurationFormatUtils.formatDurationHMS(state.getDurationOrComputeIt().toMillis());
    }

    @Benchmark
    public String newHumanDuration() {
        return state.humanDuration();
    }
}
