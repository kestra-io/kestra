package io.kestra.core.utils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.openjdk.jmh.annotations.*;

import io.kestra.core.models.flows.State;

/**
 * Compares the stream-based {@code Enums.getForNameIgnoreCase} that uppercased the value once per enum constant and
 * rebuilt the fallback map on every call with the loop-based one.
 *
 * <p>
 * Result (JDK 25, 1 fork, 3 warmup iterations of 2 s and 5 measured iterations of 3 s, {@code -prof gc}):
 * <pre>
 *                  stream                loop
 * lookup           126 ns, 824 B/op      23 ns, 136 B/op
 * </pre>
 *
 * @see <a href="https://github.com/kestra-io/kestra/pull/22484">KESTRA#22484</a>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@org.openjdk.jmh.annotations.State(Scope.Benchmark)
public class EnumsBenchmark {
    private String value;

    @Setup
    public void setup() {
        value = "success";
    }

    @Benchmark
    public State.Type oldLookup() {
        return oldGetForNameIgnoreCase(value, State.Type.class, Map.of());
    }

    @Benchmark
    public State.Type newLookup() {
        return Enums.getForNameIgnoreCase(value, State.Type.class);
    }

    private static <T extends Enum<T>> T oldGetForNameIgnoreCase(String value, Class<T> enumType, Map<String, T> fallback) {
        final Map<String, T> fallbackMap = fallback.entrySet()
            .stream()
            .collect(Collectors.toMap(entry -> entry.getKey().toUpperCase(Locale.ROOT), Map.Entry::getValue));

        T[] values = enumType.getEnumConstants();
        return Arrays.stream(values)
            .filter(e -> e.name().equals(value.toUpperCase(Locale.ROOT)))
            .findFirst()
            .or(() -> Optional.ofNullable(fallbackMap.get(value.toUpperCase(Locale.ROOT))))
            .orElseThrow(() -> new IllegalArgumentException("Unsupported enum value '" + value + "'"));
    }
}
