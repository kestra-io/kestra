package io.kestra.plugin.core.http;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@KestraTest
class SseRequestTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void basicSseConsumption() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start()
        ) {
            SseRequest task = SseRequest.builder()
                .id(SseRequestTest.class.getSimpleName())
                .type(SseRequest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/sse/simple"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            SseRequest.Output output = task.run(runContext);

            assertNotNull(output);
            assertThat(output.getSize()).isEqualTo(5);
            assertThat(output.getEvents()).hasSize(5);
            assertThat((String) output.getEvents().getFirst().data()).contains("event");
            assertThat(output.getResult()).isNull();
        }
    }

    @Test
    void sseWithJqPathExtraction() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start()
        ) {
            SseRequest task = SseRequest.builder()
                .id(SseRequestTest.class.getSimpleName())
                .type(SseRequest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/sse/json"))
                .concatJqExpression(Property.ofValue(".data.message"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            SseRequest.Output output = task.run(runContext);

            assertNotNull(output);
            assertThat(output.getSize()).isEqualTo(3);
            assertThat(output.getResult()).isNotNull();
            assertThat(output.getResult()).isEqualTo("HelloWorldTest");
        }
    }

    @Test
    void sseWithNestedJqPath() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start()
        ) {
            SseRequest task = SseRequest.builder()
                .id(SseRequestTest.class.getSimpleName())
                .type(SseRequest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/sse/nested"))
                .concatJqExpression(Property.ofValue(".data.data.value"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            SseRequest.Output output = task.run(runContext);

            assertNotNull(output);
            assertThat(output.getSize()).isEqualTo(2);
            assertThat(output.getResult()).isEqualTo("12");
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void sseWithHeaders() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start()
        ) {
            SseRequest task = SseRequest.builder()
                .id(SseRequestTest.class.getSimpleName())
                .type(SseRequest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/sse/auth"))
                .headers(Property.ofValue(ImmutableMap.of("X-API-Key", "test-key")))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            SseRequest.Output output = task.run(runContext);

            assertNotNull(output);
            assertThat(output.getSize()).isEqualTo(1);
            assertThat(((Map<String, Object>) output.getEvents().getFirst().data()).get("status")).isEqualTo("authorized");
        }
    }

    @Test
    void sseWithMixedJsonAndTextFailed() {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start()
        ) {
            SseRequest task = SseRequest.builder()
                .id(SseRequestTest.class.getSimpleName())
                .type(SseRequest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/sse/mixed"))
                .concatJqExpression(Property.ofValue(".data.count"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());
            assertThatThrownBy(() -> task.run(runContext))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Failed to resolve JQ expression '.data.count' and value '{\"data\":\"Plain text event\"}'");
        }
    }

    @Test
    void sseWithMixedJsonAndTextSuccess() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start()
        ) {
            SseRequest task = SseRequest.builder()
                .id(SseRequestTest.class.getSimpleName())
                .type(SseRequest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/sse/mixed"))
                .concatJqExpression(Property.ofValue(".data.count"))
                .failedOnMissingJq(Property.ofValue(false))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());
            SseRequest.Output output = task.run(runContext);

            assertNotNull(output);
            assertThat(output.getSize()).isEqualTo(4);
            assertThat(output.getResult()).isEqualTo("12");
        }
    }

    @Test
    void sseWithMultilineData() throws Exception {
        try (
            ApplicationContext applicationContext = ApplicationContext.run();
            EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start()
        ) {
            SseRequest task = SseRequest.builder()
                .id(SseRequestTest.class.getSimpleName())
                .type(SseRequest.class.getName())
                .uri(Property.ofValue(server.getURL().toString() + "/sse/multiline"))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, task, ImmutableMap.of());

            SseRequest.Output output = task.run(runContext);

            assertNotNull(output);
            assertThat(output.getSize()).isEqualTo(1);
            assertThat((String) output.getEvents().getFirst().data()).contains("\n");
            assertThat((String) output.getEvents().getFirst().data()).contains("line1");
            assertThat((String) output.getEvents().getFirst().data()).contains("line2");
        }
    }

    @Controller
    public static class SseController {

        @Get(uri = "/sse/simple", produces = MediaType.TEXT_EVENT_STREAM)
        public Flux<String> simpleEvents() {
            return Flux.interval(Duration.ofMillis(100))
                .take(5)
                .map(i -> "data: Simple event " + i + "\n\n");
        }

        @Get(uri = "/sse/json", produces = MediaType.TEXT_EVENT_STREAM)
        public Flux<String> jsonEvents() {
            return Flux.just(
                "data: {\"message\": \"Hello\", \"id\": 1}\n\n",
                "data: {\"message\": \"World\", \"id\": 2}\n\n",
                "data: {\"message\": \"Test\", \"id\": 3}\n\n"
            ).delayElements(Duration.ofMillis(100));
        }

        @Get(uri = "/sse/nested", produces = MediaType.TEXT_EVENT_STREAM)
        public Flux<String> nestedJsonEvents() {
            return Flux.just(
                "data: {\"data\": {\"value\": 1, \"label\": \"first\"}, \"timestamp\": 123}\n\n",
                "data: {\"data\": {\"value\": 2, \"label\": \"second\"}, \"timestamp\": 456}\n\n"
            ).delayElements(Duration.ofMillis(100));
        }

        @Get(uri = "/sse/auth", produces = MediaType.TEXT_EVENT_STREAM)
        public Flux<String> authEvents(@Header("X-API-Key") String apiKey) {
            if (!"test-key".equals(apiKey)) {
                return Flux.error(new RuntimeException("Unauthorized"));
            }
            return Flux.just("data: {\"status\": \"authorized\"}\n\n");
        }

        @Get(uri = "/sse/mixed", produces = MediaType.TEXT_EVENT_STREAM)
        public Flux<String> mixedEvents() {
            return Flux.just(
                "data: {\"count\": 1, \"type\": \"json\"}\n\n",
                "data: Plain text event\n\n",
                "data: {\"count\": 2, \"type\": \"json\"}\n\n",
                "data: Another text event\n\n"
            ).delayElements(Duration.ofMillis(100));
        }

        @Get(uri = "/sse/multiline", produces = MediaType.TEXT_EVENT_STREAM)
        public Flux<String> multilineEvents() {
            return Flux.just(
                "data: line1\ndata: line2\ndata: line3\n\n"
            );
        }
    }
}
