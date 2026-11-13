
package io.kestra.plugin.core.http;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
@io.micronaut.context.annotation.Property(name = "kestra.tasks.http.allowed-list", value = "localhost")
class WebsocketRequestAllowedListTest {

    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void shouldRejectWebSocketHostNotInAllowedList() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
                .id("websocket-allowed-list-test")
                .type("io.kestra.plugin.core.http.WebsocketRequest")
                .uri(Property.ofValue("ws://example.com/ws"))
                .build();

        RunContext runContext = TestsUtils.mockRunContext(
                runContextFactory,
                task,
                Map.of());

        Exception exception = assertThrows(
                Exception.class,
                () -> task.run(runContext));

        assertTrue(
                exception.toString().contains("configured allowed list")
                        || (exception.getCause() != null
                                && exception.getCause().getMessage() != null
                                && exception.getCause().getMessage().contains("configured allowed list")),
                "Expected allow-list rejection, but got: " + exception);
    }
}
