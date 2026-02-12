package io.kestra.plugin.core.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpSseEvent;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Consume Server-Sent Events (SSE) from an HTTP endpoint.",
    description = """
        Connects to an SSE endpoint and consumes events. Optionally extracts and concatenates specific fields from event data using JQ.

        SSE endpoints send events in the format:
        ```
        data: {"field": "value"}

        ```

        Use the `concatJqExpression` property to extract specific fields from each event's JSON data and concatenate them into a single string result."""
)
@Plugin(
    examples = {
        @Example(
            title = "Consume SSE events from an endpoint and collect all events.",
            full = true,
            code = """
                id: sse_consumer
                namespace: company.team

                tasks:
                  - id: consume_events
                    type: io.kestra.plugin.core.http.SseRequest
                    uri: https://example.com/events
                    maxEvents: 10
                """
        ),
        @Example(
            title = "Consume SSE events and extract specific field using JQ, concatenating all values.",
            full = true,
            code = """
                id: sse_with_JQ
                namespace: company.team

                tasks:
                  - id: consume_and_extract
                    type: io.kestra.plugin.core.http.SseRequest
                    uri: https://example.com/stream
                    concatJqExpression: $.message
                """
        ),
        @Example(
            title = "Consume SSE events with authentication header.",
            full = true,
            code = """
                id: sse_authenticated
                namespace: company.team

                tasks:
                  - id: auth_sse
                    type: io.kestra.plugin.core.http.SseRequest
                    uri: https://api.example.com/events
                    headers:
                      Authorization: 'Bearer {{ secret("API_TOKEN") }}'
                    concatJqExpression: $.data.value
                """
        )
    },
    metrics = {
        @Metric(name = "size", type = "counter", description = "The number of events received")
    }
)
public class SseRequest extends AbstractHttp implements RunnableTask<SseRequest.Output> {
    // Load Scope once as static to avoid repeated initialization
    // This improves performance by loading builtin functions only once when the class loads
    private static final Scope SCOPE;

    static {
        SCOPE = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, SCOPE);
    }

    @Schema(
        title = "JQ expression to extract and concatenate values from event data.",
        description = "When provided, this JQ expression will be applied to each event's, " +
            "and the extracted values will be concatenated into a single string. " +
            "If the path is not found in an event, that event will be skipped."
    )
    private Property<String> concatJqExpression;

    @Schema(
        title = "If true, the task will fail if the JQ expression is provided but fails to extract a value from any event.",
        description = "If false, events that do not match the JQ expression will be skipped " +
            "without causing the task to fail. If true, the task will throw an exception if " +
            "any event does not yield a value from the JQ expression. Default is true."
    )
    private Property<Boolean> failedOnMissingJq = Property.ofValue(true);

    @Override
    public Output run(RunContext runContext) throws Exception {
        JsonQuery jqQuery = runContext.
            render(this.concatJqExpression)
            .as(String.class)
            .map(throwFunction(pattern -> JsonQuery.compile(pattern, Versions.JQ_1_6)))
            .orElse(null);

        List<HttpSseEvent<?>> events = new ArrayList<>();

        try (HttpClient client = this.client(runContext)) {
            HttpRequest request = this.request(runContext);

            AtomicReference<Long> counter = new AtomicReference<>(0L);
            client.sseRequest(request, String.class, event -> {
                counter.getAndSet(counter.get() + 1);

                try {
                    events.add(event.clone(JacksonMapper.toObject(event.data())));
                } catch (JsonProcessingException e) {
                    events.add(event);
                }
            });

            runContext.metric(Counter.of("size", counter.get()));

            List<String> resultJq = new ArrayList<>();
            if (jqQuery != null) {
                resultJq = events
                    .stream()
                    .map(throwFunction(event -> {
                        JsonNode in = JacksonMapper.ofJson().valueToTree(event);

                        AtomicReference<String> local = new AtomicReference<>();

                        try {
                            jqQuery.apply(Scope.newChildScope(SCOPE), in, v -> {
                                if (v instanceof TextNode) {
                                    local.set(v.textValue());
                                } else if (v instanceof NumericNode) {
                                    local.set(v.numberValue().toString());
                                } else if (v instanceof BooleanNode) {
                                    local.set(v.booleanValue() ? "true" : "false");
                                } else {
                                    throw new IllegalArgumentException("Expected text value from JQ expression but got: " + v.getNodeType());
                                }
                            });
                        } catch (Exception e) {
                            String error = "Failed to resolve JQ expression '" + jqQuery + "' and value '" + in + "'";

                            if (runContext.render(failedOnMissingJq).as(Boolean.class).orElse(true)) {
                                throw new Exception(error, e);
                            } else {
                                runContext.logger().debug("{}. Skipping this event.", error);
                            }
                        }

                        return local.get();
                    }))
                    .filter(Objects::nonNull)
                    .toList();
            }

            return Output.builder()
                .uri(request.getUri())
                .events(resultJq.isEmpty() ? events : null)
                .size(counter.get())
                .result(!resultJq.isEmpty() ? String.join("", resultJq) : null)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The URL of the SSE endpoint"
        )
        private final URI uri;

        @Schema(
            title = "List of all events received",
            description = "Each event contains the json data or raw data field content from the SSE stream.. " +
                "Will be null if JQ was provided."
        )
        private final List<HttpSseEvent<?>> events;

        @Schema(
            title = "Total number of events received"
        )
        private final Long size;

        @Schema(
            title = "Concatenated result from JQ extraction",
            description = "If `concatJqExpression` was provided, this contains all extracted values concatenated into a single string. " +
                "Will be null if no JQ was provided."
        )
        private final String result;
    }
}
