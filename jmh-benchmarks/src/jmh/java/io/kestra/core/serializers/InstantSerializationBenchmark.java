package io.kestra.core.serializers;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Compares the Instant handling of {@link KestraDateTimeModule} with Jackson's built-in {@code JavaTimeModule}
 * on otherwise identical plain mappers, for an instant written with six fractional digits and a {@code Z} suffix.
 *
 * <p>
 * Result (JDK 25, 1 fork, 3 warmup and 5 measured iterations of 3 s, {@code -prof gc}):
 * <pre>
 *                  Jackson built-in      Kestra module
 * serialize        157 ns, 680 B/op      99 ns, 536 B/op
 * deserialize      658 ns, 2296 B/op     143 ns, 688 B/op
 * </pre>
 *
 * <p>
 * Only the fast path is measured; other offsets and years outside 0 to 9999 fall back to {@code DateTimeFormatter}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class InstantSerializationBenchmark {
    public record Holder(Instant instant) {}

    private static final String JSON = "{\"instant\":\"2024-06-01T10:00:00.123456Z\"}";

    private ObjectMapper kestra;
    private ObjectMapper jackson;
    private Holder holder;

    @Setup
    public void setup() {
        kestra = new ObjectMapper().registerModule(new KestraDateTimeModule());
        jackson = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        holder = new Holder(Instant.parse("2024-06-01T10:00:00.123456Z"));
    }

    @Benchmark
    public String kestraSerialize() throws Exception {
        return kestra.writeValueAsString(holder);
    }

    @Benchmark
    public String jacksonSerialize() throws Exception {
        return jackson.writeValueAsString(holder);
    }

    @Benchmark
    public Holder kestraDeserialize() throws Exception {
        return kestra.readValue(JSON, Holder.class);
    }

    @Benchmark
    public Holder jacksonDeserialize() throws Exception {
        return jackson.readValue(JSON, Holder.class);
    }
}
