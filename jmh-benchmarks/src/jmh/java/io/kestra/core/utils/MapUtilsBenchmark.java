package io.kestra.core.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class MapUtilsBenchmark {
    private Map<String, Object> mapA;
    private Map<String, Object> mapB;

    @Setup(Level.Invocation)
    public void setup() {
        mapA = new HashMap<>();
        mapB = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            mapA.put("key" + i, "valueA" + i);
            mapB.put("key" + i, "valueB" + i);
        }
        for (int i = 0; i < 10; i++) {
            mapA.put("nested" + i, Map.of("a", i));
            mapB.put("nested" + i, Map.of("b", i));
        }
        mapA.put("deepNested", Map.of("deepNested", Map.of("deepNested", "deepNestedA", "collection", List.of("mapA"))));
        mapB.put("deepNested", Map.of("deepNested", Map.of("deepNested", "deepNestedB", "collection", List.of("mapB"))));
    }

    /**
     * @see <a href="https://github.com/kestra-io/kestra/pull/8914">KESTRA#8914</a>
     * @see <a href="https://github.com/kestra-io/kestra/pull/8917">KESTRA#8917</a>
     */
    @Benchmark
    public Map<String, Object> testMerge() {
        return MapUtils.merge(mapA, mapB);
    }
}
