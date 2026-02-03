package io.kestra.plugin.core.trigger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.validations.WebhookValidation;
import io.micronaut.http.HttpRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@SuperBuilder
@ToString
@EqualsAndHashCode
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
public class Webhook extends AbstractTrigger implements TriggerOutput<Webhook.Output> {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson().copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.USE_DEFAULTS);

    @Size(max = 256)
    @NotNull
    @Schema(
        title = "The unique key that will be part of the URL.",
        description = "The key is used for generating the webhook URL.\n" +
            "\n" +
            "::alert{type=\"warning\"}\n" +
            "Make sure to keep the webhook key secure. It's the only security mechanism to protect your endpoint from bad actors, and must be considered as a secret. You can use a random key generator to create the key.\n" +
            "::\n"
    )
    @PluginProperty(dynamic = true)
    private String key;

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


    @Schema(
        title = "The inputs to pass to the triggered flow"
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

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

    public Optional<Execution> evaluate(HttpRequest<String> request, FlowInterface flow) {
        String body = request.getBody().orElse(null);

        Execution.ExecutionBuilder builder = Execution.builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .inputs(inputs)
            .variables(flow.getVariables())
            .state(new State())
            .trigger(ExecutionTrigger.of(
                this,
                Output.builder()
                    .body(tryMap(body)
                        .or(() -> tryArray(body))
                        .orElse(body)
                    )
                    .headers(request.getHeaders().asMap())
                    .parameters(request.getParameters().asMap())
                    .build()
            ));

        return Optional.of(builder.build());
    }

    private Optional<Object> tryMap(String body) {
        try {
            return Optional.of(MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {}));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<Object> tryArray(String body) {
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
