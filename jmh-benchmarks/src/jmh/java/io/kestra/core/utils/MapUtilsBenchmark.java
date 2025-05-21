package io.kestra.core.utils;

import java.util.HashMap;
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
        mapA.put("nested", Map.of("a", 1));
        mapB.put("nested", Map.of("b", 2));
    }

    @Benchmark
    public Map<String, Object> testMerge() {
        return MapUtils.merge(mapA, mapB);
    }
}
