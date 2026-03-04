package io.kestra.plugin.core.trigger;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.validations.AbstractWebhookValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Abstract base class for webhook triggers that provides common properties and execution creation logic.
 * Subclasses must implement the evaluate method to handle webhook requests.
 */
@Slf4j
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@AbstractWebhookValidation
public abstract class AbstractWebhookTrigger extends AbstractTrigger {
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

    @Schema(
        title = "The inputs to pass to the triggered flow"
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

    /**
     * Evaluate the webhook request and optionally create an execution.
     *
     * @param context The webhook context containing request, path, flow, and services
     * @return WebbookEvaluation the evaluation result containing the execution and response
     */
    public abstract Mono<HttpResponse<?>> evaluate(WebhookContext context) throws Exception;
}
