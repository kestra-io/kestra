package io.kestra.plugin.core.trigger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.validations.WebhookValidation;
import io.micronaut.http.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a Flow via an authenticated webhook URL.",
    description = """
        Exposes a signed endpoint `.../executions/webhook/{Namespace}/{flowId}/{key}` that accepts GET/POST/PUT to start a Flow. Secured by the required `key`; keep it secret.

        Request data is available as `trigger.body`, `trigger.headers`, and `trigger.parameters`. Supports `wait`/`returnOutputs` to block and return Flow outputs, and optional `responseContentType`. Conditions are allowed except `MultipleCondition`.

        Responses: 404 (not found), 200 (triggered), 204 (conditions not met)."""
)
@Plugin(
    examples = {
        @Example(
            title = "Add a webhook trigger to the current flow with the key `4wjtkzwVGBM9yKnjm3yv8r`; the webhook will be available at the URI `/api/v1/{tenant}/executions/webhook/{namespace}/{flowId}/4wjtkzwVGBM9yKnjm3yv8r`.",
            code = """
                id: webhook_flow
                namespace: company.team

                tasks:
                  - id: log_hello_world
                    type: io.kestra.plugin.core.log.Log
                    message: Hello World! 🚀

                triggers:
                  - id: webhook
                    type: io.kestra.plugin.core.trigger.Webhook
                    key: 4wjtkzwVGBM9yKnjm3yv8r
                """,
            full = true
        ),
        @Example(
            title = """
                Add a trigger matching specific webhook event condition. The flow will be executed only if the condition is met.
                """,
            code = """
                id: condition_based_webhook_flow
                namespace: company.team

                tasks:
                  - id: log_hello_world
                    type: io.kestra.plugin.core.log.Log
                    message: Hello World! 🚀

                triggers:
                  - id: webhook
                    type: io.kestra.plugin.core.trigger.Webhook
                    key: 4wjtkzwVGBM9yKnjm3yv8r
                    conditions:
                      - type: io.kestra.plugin.core.condition.Expression
                        expression: "{{ trigger.body.hello == 'world' }}"
                """,
            full = true
        ),
        @Example(
            title = """
                Webhook with text/plain response for Microsoft Graph validation handshakes.
                When a service like Microsoft Graph validates the webhook endpoint, it sends a validationToken that must be echoed back as plain text.
                """,
            code = """
                id: microsoft_graph_webhook
                namespace: company.team

                tasks:
                  - id: handle_request
                    type: io.kestra.plugin.core.debug.Return
                    format: "{{ trigger.parameters.validationToken[0] ?? 'notification processed' }}"

                outputs:
                  - id: response
                    type: STRING
                    value: "{{ outputs.handle_request.value }}"

                triggers:
                  - id: webhook
                    type: io.kestra.plugin.core.trigger.Webhook
                    key: 4wjtkzwVGBM9yKnjm3yv8r
                    wait: true
                    returnOutputs: true
                    responseContentType: "text/plain"
                """,
            full = true
        )
    },
    aliases = "io.kestra.core.models.triggers.types.Webhook"
)
@WebhookValidation
public class Webhook extends AbstractWebhookTrigger implements TriggerOutput<Webhook.Output> {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson().copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.USE_DEFAULTS);

    @PluginProperty
    @Builder.Default
    @Schema(
        title = "Wait for the flow to finish.",
        description = """
            If set to `true` the webhook call will wait for the flow to finish and return the flow outputs as response.
            If set to `false` the webhook call will return immediately after the execution is created.
           """
    )
    private Boolean wait = false;

    @PluginProperty
    @Builder.Default
    @Schema(
        title = "Send outputs of the flows as response for webhook caller.",
        description = "Requires `wait` to be `true`."
    )
    private Boolean returnOutputs = false;

    @PluginProperty
    @Schema(
        title = "Custom response content type.",
        description = """
            If set, the webhook response will use this content type instead of the default `application/json`.
            Requires `wait` and `returnOutputs` to be `true`.
            This is useful for webhook validation handshakes that require specific content types (e.g., Microsoft Graph Change Notifications require `text/plain` responses).
            """,
        allowableValues = {"application/json", "text/plain"}
    )
    private String responseContentType;

    @PluginProperty
    @Schema(
        title = "Custom response code.",
        description = """
            If set, the webhook response code will use this response code instead of the default `200`.
            Requires `wait` and `returnOutputs` to be `true`.
            """
    )
    private Property<Integer> responseCode;

    @Override
    public Mono<HttpResponse<?>> evaluate(WebhookContext context) throws Exception {
        // Reject path since not expected
        if (context.path() != null || context.request().getUri().getPath().endsWith("/")) {
            return Mono.just(HttpResponse.of(HttpResponse.Status.NOT_FOUND));
        }

        String body = context.request().getBody() != null ? (String) context.request().getBody().getContent() : null;

        Optional<Execution> maybeExecution = context.webhookService().newExecution(
            context,
            context.flow(),
            this,
            Webhook.Output.builder()
                .body(tryMap(body)
                    .or(() -> tryArray(body))
                    .orElse(body)
                )
                .headers(context.request().getHeaders() != null ? context.request().getHeaders().map() : null)
                .parameters(context.webhookService().parseParameters(context))
                .build()
        );

        if (maybeExecution.isEmpty()) {
            return Mono.just(HttpResponse.of(HttpResponse.Status.CONFLICT));
        }

        Execution execution = maybeExecution.get();

        try {
            context.webhookService().startExecution(execution);
        } catch (QueueException e) {
            return Mono.just(HttpResponse.of(HttpResponse.Status.INTERNAL_SERVER_ERROR));
        }

        if (!this.wait) {
            return Mono.just(HttpResponse.of(context.webhookService().executionResponse(execution)));
        }

        return context
            .webhookService()
            .followExecution(execution, context.flow())
            .last()
            .map(throwFunction(event -> {
                RunContext runContext = context.webhookService().runContext(context.flow(), event.getData());
                int responseCode = runContext.render(this.responseCode).as(Integer.class).orElse(event.getData().getState().isFailed() ? 500 : 200);

                if (this.getReturnOutputs()) {
                    return buildOutputResponse(event.getData().getOutputs(), responseContentType, HttpResponse.Status.valueOf(responseCode));
                } else {
                    return HttpResponse.of(HttpResponse.Status.valueOf(responseCode), context.webhookService().executionResponse(event.getData()));
                }
            }));
    }

    private HttpResponse<?> buildOutputResponse(Object body, String responseContentType, HttpResponse.Status responseCode) {
        if (responseContentType != null && responseContentType.equals(MediaType.TEXT_PLAIN)) {
            String responseBody;
            if (body instanceof String s) {
                responseBody = s;
            } else {
                try {
                    responseBody = MAPPER.writeValueAsString(body);
                } catch (Exception e) {
                    responseBody = String.valueOf(body);
                }
            }

            return HttpResponse.of(responseCode, responseBody, MediaType.TEXT_PLAIN_TYPE.toString());
        }

        // Default: application/json (or no responseContentType set)
        return HttpResponse.of(responseCode, body, responseContentType);
    }

    private static Optional<Object>  tryMap(String body) {
        try {
            return Optional.of(MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {}));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> tryArray(String body) {
        try {
            return Optional.of(MAPPER.readValue(body, new TypeReference<List<Object>>() {}));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Builder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The full body for the webhook request",
            description = "We try to deserialize the incoming request as JSON (array or object).\n" +
                "If we can't deserialize, the full body will be available as a string."
        )
        @NotNull
        private Object body;

        @Schema(title = "The headers for the webhook request")
        @NotNull
        private Map<String, List<String>> headers;

        @Schema(title = "The parameters for the webhook request")
        @NotNull
        private Map<String, List<String>> parameters;
    }
}
