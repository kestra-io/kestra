package io.kestra.core.queues;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueServiceTest {

    @Test
    void shouldReturnUidWhenObjectImplementsHasUid() {
        HasUID object = () -> "uid";

        assertThat(QueueService.key(object)).isEqualTo("uid");
    }

    @Test
    void shouldReturnNullWhenObjectIsLogEntry() {
        LogEntry object = LogEntry.builder().build();

        assertThat(QueueService.key(object)).isNull();
    }

    @Test
    void shouldReturnNullWhenObjectIsMetricEntry() {
        MetricEntry object = MetricEntry.builder().build();

        assertThat(QueueService.key(object)).isNull();
    }

    @Test
    void shouldPreserveNullUidWhenHasUidReturnsNull() {
        HasUID object = () -> null;

        assertThat(QueueService.key(object)).isNull();
    }

    @Test
    void shouldThrowExceptionWhenObjectIsNull() {
        assertThatThrownBy(() -> QueueService.key(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowExceptionWhenObjectHasUnsupportedType() {
        Object object = new Object();

        assertThatThrownBy(() -> QueueService.key(object))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown type 'java.lang.Object'");
    }
}
