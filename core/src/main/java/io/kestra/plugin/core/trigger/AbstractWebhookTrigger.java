package io.kestra.plugin.core.trigger;

import java.util.Map;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.triggers.AbstractTrigger;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

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
public abstract class AbstractWebhookTrigger extends AbstractTrigger {
    @Size(max = 256)
    @NotBlank
    @Schema(
        title = "The unique key that will be part of the URL.",
        description = "The key is used for generating the webhook URL.\n" +
            "\n" +
            "::alert{type=\"warning\"}\n" +
            "Make sure to keep the webhook key secure. It's the only security mechanism to protect your endpoint from bad actors, and must be considered as a secret. You can use a random key generator to create the key.\n"
            +
            "::\n"
    )
    @PluginProperty(dynamic = true)
    private String key;

    @Schema(
        title = "The inputs to pass to the triggered flow"
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

    @Schema(
        title = "What the trigger does with the body of the webhook request.",
        description = """
            - `FETCH`: the body reaches the flow on the `body` output. A JSON body is deserialized, a binary one is \
            base64-encoded. This is the default, and how a webhook has always behaved.
            - `STORE`: the body is streamed into Kestra's internal storage as it is received, and the flow reaches it \
            through the `uri` output. Nothing of it travels through the execution, so this is the option to use for a \
            large or binary payload - but note that a condition on the trigger can no longer read the body.
            - `NONE`: the body is read off the connection and dropped. Use it for a caller whose payload the flow does \
            not need.

            This only concerns the body of a request. The file parts of a `multipart/form-data` request are always \
            stored in the internal storage and exposed on `parts`, whatever this property is set to, as a file part \
            has no meaningful representation inside an execution."""
    )
    @NotNull
    @PluginProperty
    @Builder.Default
    private FetchType fetchType = FetchType.FETCH;

    /**
     * {@return what the trigger does with the body of the webhook request, {@link FetchType#FETCH} by default}
     * Defaulted here as well as on the builder, as a trigger deserialized from a flow that does not set the
     * property carries no value for it.
     */
    public FetchType getFetchType() {
        return fetchType == null ? FetchType.FETCH : fetchType;
    }

    /**
     * Evaluate the webhook request and optionally create an execution.
     *
     * @param context The webhook context containing request, path, flow, and services
     * @return WebbookEvaluation the evaluation result containing the execution and response
     */
    public abstract Mono<HttpResponse<?>> evaluate(WebhookContext context) throws Exception;

    /**
     * What a webhook trigger does with the body of the request it receives.
     */
    public enum FetchType {
        /** The body is not read into the flow. */
        NONE,

        /** The body is exposed on the {@code body} output. */
        FETCH,

        /** The body is stored in Kestra's internal storage, and its URI exposed on the {@code uri} output. */
        STORE
    }
}
