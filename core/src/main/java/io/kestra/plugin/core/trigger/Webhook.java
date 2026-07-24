package io.kestra.plugin.core.trigger;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.validations.WebhookValidation;

import io.micronaut.http.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Mono;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a Flow via an authenticated webhook URL.",
    description = """
        Exposes a signed endpoint `.../executions/webhook/{Namespace}/{flowId}/{key}` that accepts GET/POST/PUT to start a Flow. Secured by the required `key`; keep it secret.

        Request data is available as `trigger.body`, `trigger.headers`, and `trigger.parameters`. A binary body is available base64-encoded as `trigger.bodyBase64`, and a `multipart/form-data` body as `trigger.parts` (files, content base64-encoded) and `trigger.formFields`. Supports `wait`/`returnOutputs` to block and return Flow outputs, and optional `responseContentType`. Conditions are allowed except `MultipleCondition`.

        Responses: 404 (not found), 200 (triggered), 204 (conditions not met), 422 (inputs could not be rendered)."""
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
                    when: "{{ trigger.body.hello == 'world' }}"
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
        ),
        @Example(
            title = """
                Receive a file uploaded as `multipart/form-data`. Each file part is available under `trigger.parts`,
                with its content base64-encoded so that binary content reaches the flow intact, and the parts that are
                not files under `trigger.formFields`.
                """,
            code = """
                id: upload_webhook
                namespace: company.team

                tasks:
                  - id: log_upload
                    type: io.kestra.plugin.core.log.Log
                    message: "Received {{ trigger.parts[0].filename }} ({{ trigger.parts[0].size }} bytes, {{ trigger.parts[0].contentType }}), note: {{ trigger.formFields.note[0] }}"

                triggers:
                  - id: webhook
                    type: io.kestra.plugin.core.trigger.Webhook
                    key: 4wjtkzwVGBM9yKnjm3yv8r
                """,
            full = true
        )
    }
)
@WebhookValidation
public class Webhook extends AbstractWebhookTrigger implements TriggerOutput<Webhook.Output> {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson().copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.USE_DEFAULTS);

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

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
        allowableValues = { "application/json", "text/plain" }
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

        Output.OutputBuilder output = Output.builder()
            .headers(context.request().getHeaders() != null ? context.request().getHeaders().map() : null)
            .parameters(context.webhookService().parseParameters(context));

        Optional<Execution> maybeExecution;
        try {
            maybeExecution = context.webhookService().newExecution(
                context,
                context.flow(),
                this,
                withBody(output, context.request().getBody()).build()
            );
        } catch (WebhookInputRenderException e) {
            // The inputs could not be rendered: a real error, not a "conditions not met" outcome.
            return Mono.just(HttpResponse.of(HttpResponse.Status.UNPROCESSABLE_ENTITY));
        }

        if (maybeExecution.isEmpty()) {
            // Conditions are not met: no execution is created, return 204 as documented.
            return Mono.just(HttpResponse.of(HttpResponse.Status.NO_CONTENT));
        }

        Execution execution = maybeExecution.get();

        return context.webhookService().startExecution(execution)
            .flatMap(__ ->
            {
                if (!this.wait) {
                    return Mono.<HttpResponse<?>> just(HttpResponse.of(context.webhookService().executionResponse(execution)));
                }

                return context
                    .webhookService()
                    .followExecution(execution, context.flow())
                    .last()
                    .flatMap(event ->
                    {
                        try {
                            RunContext runContext = context.webhookService().runContext(context.flow(), event.getData());
                            int responseCode = runContext.render(this.responseCode).as(Integer.class).orElse(event.getData().getState().isFailed() ? 500 : 200);

                            HttpResponse<?> response = this.getReturnOutputs()
                                ? buildOutputResponse(event.getData().getOutputs(), responseContentType, HttpResponse.Status.valueOf(responseCode))
                                : HttpResponse.of(HttpResponse.Status.valueOf(responseCode), context.webhookService().executionResponse(event.getData()));
                            return Mono.<HttpResponse<?>> just(response);
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    });
            })
            .onErrorReturn(HttpResponse.of(HttpResponse.Status.INTERNAL_SERVER_ERROR));
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

    /**
     * Expose the request body on the trigger output: a text body as {@code body}, a binary body as
     * {@code bodyBase64}, and a {@code multipart/form-data} body as {@code parts} and {@code formFields}.
     * Bytes are base64-encoded because trigger variables carry text, not binary.
     *
     * @param output      the output being built
     * @param requestBody the body of the webhook request, {@code null} if the request has none
     * @return the output builder, for chaining
     */
    private static Output.OutputBuilder withBody(Output.OutputBuilder output, HttpRequest.RequestBody requestBody) {
        return switch (requestBody) {
            case null -> output;
            case HttpRequest.MultipartFormDataRequestBody multipart -> withMultipartBody(output, multipart);
            case HttpRequest.ByteArrayRequestBody binary -> output.bodyBase64(BASE64_ENCODER.encodeToString(binary.getContent()));
            case HttpRequest.StringRequestBody text -> {
                String body = text.getContent();

                yield output.body(
                    tryMap(body)
                        .or(() -> tryArray(body))
                        .orElse(body)
                );
            }
            default -> output.body(requestBody.getContent());
        };
    }

    private static Output.OutputBuilder withMultipartBody(Output.OutputBuilder output, HttpRequest.MultipartFormDataRequestBody multipart) {
        Charset charset = multipart.getCharset() != null ? multipart.getCharset() : StandardCharsets.UTF_8;
        List<Output.Part> parts = new ArrayList<>(multipart.getContent().size());
        Map<String, List<String>> formFields = new HashMap<>();

        multipart.getContent().forEach(part ->
        {
            if (part.isFile()) {
                parts.add(new Output.Part(
                    part.name(),
                    part.filename(),
                    part.contentType(),
                    part.content().length,
                    BASE64_ENCODER.encodeToString(part.content())
                ));
            } else {
                formFields.computeIfAbsent(part.name(), name -> new ArrayList<>()).add(new String(part.content(), charset));
            }
        });

        return output.parts(parts).formFields(formFields);
    }

    private static Optional<Object> tryMap(String body) {
        try {
            return Optional.of(MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {
            }));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> tryArray(String body) {
        try {
            return Optional.of(MAPPER.readValue(body, new TypeReference<List<Object>>() {
            }));
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
                "If we can't deserialize, the full body will be available as a string.\n" +
                "Only set for a text body: a binary body is available as `bodyBase64`, and a `multipart/form-data` body as `parts` and `formFields`."
        )
        @NotNull
        private Object body;

        @Schema(
            title = "The base64-encoded body for the webhook request",
            description = "Only set when the request body is binary, i.e. when its content type is neither text, JSON, XML, " +
                "form-urlencoded, YAML nor CSV; base64 is how bytes travel through the text-based trigger variables."
        )
        private String bodyBase64;

        @Schema(
            title = "The file parts of a `multipart/form-data` webhook request",
            description = "Only set for a `multipart/form-data` request. Each part carries its content base64-encoded, " +
                "so that binary content such as an image reaches the flow intact."
        )
        private List<Part> parts;

        @Schema(
            title = "The form fields of a `multipart/form-data` webhook request",
            description = "Only set for a `multipart/form-data` request; holds the parts that are not files."
        )
        private Map<String, List<String>> formFields;

        @Schema(title = "The headers for the webhook request")
        @NotNull
        private Map<String, List<String>> headers;

        @Schema(title = "The parameters for the webhook request")
        @NotNull
        private Map<String, List<String>> parameters;

        /**
         * A file part of a {@code multipart/form-data} webhook request.
         *
         * @param name        the form field name of the part
         * @param filename    the name of the uploaded file
         * @param contentType the content type of the part, {@code null} if the caller did not send one
         * @param size        the size of the part content in bytes, before base64 encoding
         * @param content     the part content, base64-encoded
         */
        @Schema(title = "A file part of a `multipart/form-data` webhook request")
        public record Part(String name, String filename, String contentType, int size, String content) {
        }
    }
}
