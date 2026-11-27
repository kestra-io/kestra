package io.kestra.webserver.controllers.api;

import io.kestra.core.models.executions.Execution;
import io.micronaut.http.sse.Event;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionFollowHeartbeatTest {
    @Test
    void shouldEmitStatelessHeartbeatsWhileTheStreamIsOpen() {
        List<Event<Execution>> events = Flux.<Event<Execution>>create(emitter ->
                ExecutionController.emitHeartbeats(emitter, "exec-1", Duration.ofMillis(10)))
            .take(2)
            .collectList()
            .block(Duration.ofSeconds(5));

        assertThat(events).hasSize(2).allSatisfy(event -> {
            assertThat(event.getId()).isEqualTo(ExecutionController.FOLLOW_HEARTBEAT_ID);
            assertThat(event.getData().getId()).isEqualTo("exec-1");
            assertThat(event.getData().getState()).isNull();
        });
    }
}
