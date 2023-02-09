package io.kestra.core.models.triggers.types;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.PluginProperty;
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
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow from a webhook",
    description = "Webbook trigger allow you to trigger a flow from a webhook url.\n" +
        "The trigger will generate a `key` that must be used on url : `/api/v1/executions/webhook/{namespace}/[flowId]/{key}`.\n" +
        "Kestra accept `GET`, `POST` & `PUT` request on this url.\n" +
        "The whole body & headers will be available as variable:\n " +
        "- `trigger.variables.body`\n" +
        "- `trigger.variables.headers`"
)
@Plugin(
    examples = {
        @Example(
            title = "Add a trigger to the current flow",
            code = {
                "triggers:",
                "  - id: webhook",
                "    type: io.kestra.core.models.triggers.types.Webhook"
            },
            full = true
        ),
        @Example(
            title = "After the trigger is created, a key will be created that will be use in the webhook url, now, you can launch " +
                "the flow on the url `/api/v1/executions/webhook/{namespace}/[flowId]/4wjtkzwVGBM9yKnjm3yv8r`",
            code = {
                "triggers:",
                "  - id: webhook",
                "    type: io.kestra.core.models.triggers.types.Webhook",
                "    key: 4wjtkzwVGBM9yKnjm3yv8r"
            },
            full = true
        )
    }
)
public class Webhook extends AbstractTrigger implements TriggerOutput<Webhook.Output> {
    @Builder.Default
    @Size(min = 16, max = 256)
    @Schema(
        title = "The unique key that will be part of the url",
        description = "If you don't provide a key, a random one will be generated. Is used as key for generating the url of the webhook.\n" +
            "\n" +
            "::: warning\n" +
            "Take care when using manual key, the key is the only security to protect your webhook and must be considered as a secret !\n" +
            ":::\n",
        defaultValue = "<generated-hash>"
    )
    @PluginProperty
    private final String key = IdUtils.create();

    public Optional<Execution> evaluate(HttpRequest<String> request, io.kestra.core.models.flows.Flow flow) {
        String body = request.getBody().orElse(null);

        ObjectMapper mapper = JacksonMapper.ofJson();

        Execution.ExecutionBuilder builder = Execution.builder()
            .id(IdUtils.create())
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
            title = "The full body for the webhook request",
            description = "We try to deserialize the incoming request as json (array or object).\n" +
                "If we can't the full body as string will be available"
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
