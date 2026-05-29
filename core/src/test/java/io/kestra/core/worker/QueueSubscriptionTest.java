package io.kestra.core.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.serializers.JacksonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueSubscriptionTest {

    @Test
    void shouldAcceptNoReservationSentinel() {
        // Given / When
        QueueSubscription sub = new QueueSubscription("gpu", QueueSubscription.NO_RESERVATION);

        // Then
        assertThat(sub.workerQueueId()).isEqualTo("gpu");
        assertThat(sub.reservedPercent()).isEqualTo(-1);
        assertThat(sub.hasReservation()).isFalse();
    }

    @Test
    void shouldAcceptReservedPercentInRange() {
        assertThat(new QueueSubscription("gpu", 1).reservedPercent()).isEqualTo(1);
        assertThat(new QueueSubscription("gpu", 100).reservedPercent()).isEqualTo(100);
        assertThat(new QueueSubscription("gpu", 50).reservedPercent()).isEqualTo(50);
        assertThat(new QueueSubscription("gpu", 50).hasReservation()).isTrue();
    }

    @Test
    void shouldRejectReservedPercentOutOfRange() {
        assertThatThrownBy(() -> new QueueSubscription("gpu", 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueueSubscription("gpu", 101))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueueSubscription("gpu", -2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullOrBlankWorkerQueueId() {
        assertThatThrownBy(() -> new QueueSubscription(null, QueueSubscription.NO_RESERVATION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("workerQueueId");
        assertThatThrownBy(() -> new QueueSubscription("", QueueSubscription.NO_RESERVATION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("workerQueueId");
        assertThatThrownBy(() -> new QueueSubscription("   ", QueueSubscription.NO_RESERVATION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("workerQueueId");
    }

    @Test
    void defaultShouldTargetDefaultQueueWithNoReservation() {
        assertThat(QueueSubscription.DEFAULT.reservedPercent()).isEqualTo(-1);
        assertThat(QueueSubscription.DEFAULT.workerQueueId()).isEqualTo(WorkerQueues.DEFAULT_ID);
        assertThat(QueueSubscription.DEFAULT.hasReservation()).isFalse();
    }

    @Test
    void shouldDefaultToStrictModeWhenModeOmitted() {
        QueueSubscription sub = new QueueSubscription("gpu", 30);
        assertThat(sub.mode()).isEqualTo(QueueSubscription.Mode.STRICT);
        assertThat(sub.isElastic()).isFalse();
    }

    @Test
    void shouldDefaultToStrictModeWhenModeIsNull() {
        QueueSubscription sub = new QueueSubscription("gpu", 30, null);
        assertThat(sub.mode()).isEqualTo(QueueSubscription.Mode.STRICT);
        assertThat(sub.isElastic()).isFalse();
    }

    @Test
    void shouldReportElasticWhenModeIsElastic() {
        QueueSubscription sub = new QueueSubscription("gpu", 30, QueueSubscription.Mode.ELASTIC);
        assertThat(sub.mode()).isEqualTo(QueueSubscription.Mode.ELASTIC);
        assertThat(sub.isElastic()).isTrue();
    }

    @Test
    void shouldCoerceUnknownModeToStrictInConstructor() {
        QueueSubscription sub = new QueueSubscription("gpu", 30, QueueSubscription.Mode.UNKNOWN);
        assertThat(sub.mode()).isEqualTo(QueueSubscription.Mode.STRICT);
        assertThat(sub.isElastic()).isFalse();
    }

    @Test
    void shouldDeserializeUnknownModeStringAsStrict() throws Exception {
        // Given: JSON with a mode value that isn't STRICT or ELASTIC (e.g., a future
        // mode rolled back, or a typo from an external integration). Without the
        // JsonCreator + UNKNOWN coercion this would throw InvalidFormatException
        // and break the whole pool load.
        ObjectMapper mapper = JacksonMapper.ofJson();
        String json = "{\"workerQueueId\":\"gpu\",\"reservedPercent\":30,\"mode\":\"BURSTABLE\"}";

        // When
        QueueSubscription sub = mapper.readValue(json, QueueSubscription.class);

        // Then
        assertThat(sub.mode()).isEqualTo(QueueSubscription.Mode.STRICT);
    }

    @Test
    void shouldDeserializeMissingModeAsStrict() throws Exception {
        // Given: legacy / pre-Mode payload with no mode field at all.
        ObjectMapper mapper = JacksonMapper.ofJson();
        String json = "{\"workerQueueId\":\"gpu\",\"reservedPercent\":30}";

        // When
        QueueSubscription sub = mapper.readValue(json, QueueSubscription.class);

        // Then
        assertThat(sub.mode()).isEqualTo(QueueSubscription.Mode.STRICT);
    }
}
