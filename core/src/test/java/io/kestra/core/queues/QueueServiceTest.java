package io.kestra.core.queues;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueServiceTest {

    private final QueueService queueService = new QueueService();

    @Test
    void shouldReturnUidWhenObjectImplementsHasUid() {
        HasUID object = () -> "uid";

        assertThat(queueService.key(object)).isEqualTo("uid");
    }

    @Test
    void shouldReturnNullWhenObjectIsLogEntry() {
        LogEntry object = LogEntry.builder().build();

        assertThat(queueService.key(object)).isNull();
    }

    @Test
    void shouldReturnNullWhenObjectIsMetricEntry() {
        MetricEntry object = MetricEntry.builder().build();

        assertThat(queueService.key(object)).isNull();
    }

    @Test
    void shouldPreserveNullUidWhenHasUidReturnsNull() {
        HasUID object = () -> null;

        assertThat(queueService.key(object)).isNull();
    }

    @Test
    void shouldThrowExceptionWhenObjectIsNull() {
        assertThatThrownBy(() -> queueService.key(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowExceptionWhenObjectHasUnsupportedType() {
        Object object = new Object();

        assertThatThrownBy(() -> queueService.key(object))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown type 'java.lang.Object'");
    }
}
