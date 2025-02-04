package io.kestra.plugin.core.trigger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.PluginProperty;
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
    title = "Trigger a flow from a webhook.",
    description = """
        Webhook trigger allows you to create a unique URL that you can use to trigger a Kestra flow execution based on events in another application such as GitHub or Amazon EventBridge. In order to use that URL, you have to add a secret key that will secure your webhook URL.

        The URL will then follow the following format: `https://{your_hostname}/api/v1/executions/webhook/{namespace}/{flowId}/{key}`. Replace the templated values according to your workflow setup.

        The webhook URL accepts `GET`, `POST` and `PUT` requests.

        You can access the request body and headers sent by another application using the following template variables:
        - `{{ trigger.body }}`
        - `{{ trigger.headers }}`.

        The webhook response will be one of the following HTTP status codes:
        - 404 if the namespace, flow or webhook key is not found.
        - 200 if the webhook triggers an execution.
        - 204 if the webhook cannot trigger an execution due to a lack of matching event conditions sent by other application.

        A webhook trigger can have conditions but it doesn't support conditions of type `MultipleCondition`."""
)
@Plugin(
    examples = {
        @Example(
            title = "Add a webhook trigger to the current flow with the key `4wjtkzwVGBM9yKnjm3yv8r`, the webhook will be available at the URI `/api/v1/executions/webhook/{namespace}/{flowId}/4wjtkzwVGBM9yKnjm3yv8r`.",
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
                Add a trigger matching specific webhook event condition. The flow will be executed only if the condition is met.`.
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
        )
    },
    aliases = "io.kestra.core.models.triggers.types.Webhook"
)
@WebhookValidation
public class Webhook extends AbstractTrigger implements TriggerOutput<Webhook.Output> {
    @Size(max = 256)
    @NotNull
    @Schema(
        title = "The unique key that will be part of the URL.",
        description = "The key is used for generating the URL of the webhook.\n" +
            "\n" +
            "::alert{type=\"warning\"}\n" +
            "Make sure to keep the webhook key secure. It's the only security mechanism to protect your endpoint from bad actors, and must be considered as a secret. You can use a random key generator to create the key.\n" +
            "::\n"
    )
    @PluginProperty(dynamic = true)
    private String key;

    public Optional<Execution> evaluate(HttpRequest<String> request, io.kestra.core.models.flows.Flow flow) {
        String body = request.getBody().orElse(null);

        ObjectMapper mapper = JacksonMapper.ofJson().setSerializationInclusion(JsonInclude.Include.USE_DEFAULTS);

        Execution.ExecutionBuilder builder = Execution.builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .trigger(ExecutionTrigger.of(
                this,
                Output.builder()
                    .body(tryMap(mapper, body)
                        .or(() -> tryArray(mapper, body))
                        .orElse(body)
                    )
                    .headers(request.getHeaders().asMap())
                    .parameters(request.getParameters().asMap())
                    .build()
            ));

        return Optional.of(builder.build());
    }

    private Optional<Object> tryMap(ObjectMapper mapper, String body) {
        try {
            return Optional.of(mapper.readValue(body, new TypeReference<Map<String, Object>>() {}));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<Object> tryArray(ObjectMapper mapper, String body) {
        try {
            return Optional.of(mapper.readValue(body, new TypeReference<List<Object>>() {}));
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
            title = "The full body for the webhook request.",
            description = "We try to deserialize the incoming request as JSON (array or object).\n" +
                "If we can't deserialize, the full body as string will be available."
        )
        @NotNull
        private Object body;

        @Schema(title = "The headers for the webhook request.")
        @NotNull
        private Map<String, List<String>> headers;


        @Schema(title = "The parameters for the webhook request.")
        @NotNull
        private Map<String, List<String>> parameters;
    }
}
