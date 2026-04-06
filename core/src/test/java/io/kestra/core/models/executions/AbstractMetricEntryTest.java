package io.kestra.core.models.executions;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Gauge;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractMetricEntryTest {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Test
    void shouldDeserializeCounter() throws JsonProcessingException {
        Counter counter = Counter.of("my.counter", "A counter", 42D);
        String json = MAPPER.writeValueAsString(counter);
        AbstractMetricEntry<?> deserialized = MAPPER.readValue(json, AbstractMetricEntry.class);

        assertThat(deserialized).isInstanceOf(Counter.class);
        assertThat(deserialized.getType()).isEqualTo("counter");
        assertThat(deserialized.getName()).isEqualTo("my.counter");
        assertThat(((Counter) deserialized).getValue()).isEqualTo(42D);
    }

    @Test
    void shouldDeserializeTimer() throws JsonProcessingException {
        Timer timer = Timer.of("my.timer", "A timer", Duration.ofSeconds(5));
        String json = MAPPER.writeValueAsString(timer);
        AbstractMetricEntry<?> deserialized = MAPPER.readValue(json, AbstractMetricEntry.class);

        assertThat(deserialized).isInstanceOf(Timer.class);
        assertThat(deserialized.getType()).isEqualTo("timer");
        assertThat(deserialized.getName()).isEqualTo("my.timer");
        assertThat(((Timer) deserialized).getValue()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void shouldDeserializeGauge() throws JsonProcessingException {
        Gauge gauge = Gauge.of("my.gauge", "A gauge", 99.5D);
        String json = MAPPER.writeValueAsString(gauge);
        AbstractMetricEntry<?> deserialized = MAPPER.readValue(json, AbstractMetricEntry.class);

        assertThat(deserialized).isInstanceOf(Gauge.class);
        assertThat(deserialized.getType()).isEqualTo("gauge");
        assertThat(deserialized.getName()).isEqualTo("my.gauge");
        assertThat(((Gauge) deserialized).getValue()).isEqualTo(99.5D);
    }
}
