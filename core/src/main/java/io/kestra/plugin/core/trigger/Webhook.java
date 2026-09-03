package io.kestra.plugin.core.trigger;

import java.net.URI;
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

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.queues.MessageTooBigException;
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
import reactor.core.Exceptions;
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

        Request data is available as `trigger.body`, `trigger.headers`, and `trigger.parameters`. A binary body is base64-encoded on `trigger.body`, unless `fetchType` is set to `STORE` to stream it into Kestra's internal storage and expose it as `trigger.uri` instead. A `multipart/form-data` body is available as `trigger.parts` (files, always stored in Kestra's internal storage) and `trigger.formFields`. Supports `wait`/`returnOutputs` to block and return Flow outputs, and optional `responseContentType`. Conditions are allowed except `MultipleCondition`.

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
                its content stored in Kestra's internal storage and reachable through its `uri`, and the parts that are
                not files under `trigger.formFields`.
                """,
            code = """
                id: upload_webhook
                namespace: company.team

                tasks:
                  - id: log_upload
                    type: io.kestra.plugin.core.log.Log
                    message: "Received {{ trigger.parts[0].filename }} ({{ trigger.parts[0].size }} bytes, {{ trigger.parts[0].contentType }}), note: {{ trigger.formFields.note[0] }}"

                  - id: measure_upload
                    type: io.kestra.plugin.core.storage.Size
                    uri: "{{ trigger.parts[0].uri }}"

                triggers:
                  - id: webhook
                    type: io.kestra.plugin.core.trigger.Webhook
                    key: 4wjtkzwVGBM9yKnjm3yv8r
                """,
            full = true
        ),
        @Example(
            title = """
                Receive a payload that is not a form - a large JSON export, or a binary file sent as the request body -
                without carrying it through the execution. `fetchType: STORE` streams the body into Kestra's internal
                storage and exposes its URI as `trigger.uri` instead of its content as `trigger.body`. Note that a
                condition on the trigger can no longer read the body.
                """,
            code = """
                id: stored_body_webhook
                namespace: company.team

                tasks:
                  - id: measure_body
                    type: io.kestra.plugin.core.storage.Size
                    uri: "{{ trigger.uri }}"

                triggers:
                  - id: webhook
                    type: io.kestra.plugin.core.trigger.Webhook
                    key: 4wjtkzwVGBM9yKnjm3yv8r
                    fetchType: STORE
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

        Optional<Execution> maybeExecution;
        try {
            maybeExecution = context.webhookService().newExecution(
                context,
                context.flow(),
                this,
                buildOutput(context, context.request().getBody(), context.storedBodyUri())
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
                    try {
                        return Mono.<HttpResponse<?>> just(HttpResponse.of(context.webhookService().executionResponse(execution)));
                    } catch (InternalException e) {
                        return Mono.error(e);
                    }
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
                                ? buildOutputResponse(context.webhookService().executionOutputs(event.getData()), responseContentType, HttpResponse.Status.valueOf(responseCode))
                                : HttpResponse.of(HttpResponse.Status.valueOf(responseCode), context.webhookService().executionResponse(event.getData()));
                            return Mono.<HttpResponse<?>> just(response);
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    });
            })
            .onErrorResume(e ->
            {
                // A body too large to enqueue is propagated so the ErrorController handler maps it to 413; any
                // other error becomes a 500.
                Throwable unwrapped = Exceptions.unwrap(e);
                if (unwrapped instanceof MessageTooBigException) {
                    return Mono.error(unwrapped);
                }
                return Mono.just(HttpResponse.of(HttpResponse.Status.INTERNAL_SERVER_ERROR));
            });
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
     * Build the trigger output from the webhook request: its headers and query parameters, plus the body as
     * {@code body} for a text body, as {@code body} base64-encoded for a binary one, and as {@code parts} and
     * {@code formFields} for a {@code multipart/form-data} one.
     * Bytes are base64-encoded because trigger variables carry text, not binary.
     * <p>
     * A body the trigger asked to store never reaches here: it carries no content but the URI it was stored
     * under, which is exposed as {@code uri}.
     *
     * @param context       the webhook request context
     * @param requestBody   the body of the webhook request, {@code null} if the request has none, or if it was
     *                      stored rather than read
     * @param storedBodyUri the URI the body was stored under, {@code null} unless the trigger stores it
     * @return the trigger output
     */
    private static Output buildOutput(WebhookContext context, HttpRequest.RequestBody requestBody, URI storedBodyUri) {
        var output = Output.builder()
            .headers(context.request().getHeaders() != null ? context.request().getHeaders().map() : null)
            .parameters(context.webhookService().parseParameters(context));

        if (storedBodyUri != null) {
            return output.uri(storedBodyUri.toString()).build();
        }

        switch (requestBody) {
            case null -> { }
            case HttpRequest.MultipartFormDataRequestBody multipart -> {
                MultipartContent content = multipartContent(multipart);
                output.parts(content.parts()).formFields(content.formFields());
            }
            case HttpRequest.ByteArrayRequestBody binary -> output.body(BASE64_ENCODER.encodeToString(binary.getContent()));
            case HttpRequest.StringRequestBody text -> {
                String body = text.getContent();

                output.body(
                    tryMap(body)
                        .or(() -> tryArray(body))
                        .orElse(body)
                );
            }
            default -> output.body(requestBody.getContent());
        }

        return output.build();
    }

    private static MultipartContent multipartContent(HttpRequest.MultipartFormDataRequestBody multipart) {
        Charset charset = multipart.getCharset() != null ? multipart.getCharset() : StandardCharsets.UTF_8;
        List<Output.Part> parts = new ArrayList<>(multipart.getContent().size());
        Map<String, List<String>> formFields = new HashMap<>();

        multipart.getContent().forEach(part ->
        {
            switch (part) {
                case HttpRequest.MultipartFormDataRequestBody.FilePart file -> parts.add(Output.Part.builder()
                    .name(file.name())
                    .filename(file.filename())
                    .contentType(file.contentType())
                    .size(file.size())
                    .uri(file.uri().toString())
                    .build());
                case HttpRequest.MultipartFormDataRequestBody.FormFieldPart formField ->
                    formFields.computeIfAbsent(formField.name(), name -> new ArrayList<>()).add(new String(formField.content(), charset));
            }
        });

        return new MultipartContent(parts, formFields);
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

    private record MultipartContent(List<Output.Part> parts, Map<String, List<String>> formFields) {}

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
                "A binary body - one whose content type is neither text, JSON, XML, form-urlencoded, YAML nor CSV - is " +
                "base64-encoded, as base64 is how bytes travel through the text-based trigger variables. Use the " +
                "`content-type` request header to tell a base64-encoded body apart from a plain text one.\n" +
                "Only set for a trigger whose `fetchType` is `FETCH`, and not for a `multipart/form-data` request, " +
                "which is available as `parts` and `formFields`."
        )
        @NotNull
        private Object body;

        @Schema(
            title = "The URI of the body of the webhook request in Kestra's internal storage",
            description = "Only set for a trigger whose `fetchType` is `STORE`. The body is streamed to the " +
                "internal storage as it is received, so that a payload of any size reaches the flow intact and " +
                "without travelling through the execution. It is stored under the execution the webhook call " +
                "creates, and purged with it."
        )
        private String uri;

        @Schema(
            title = "The file parts of a `multipart/form-data` webhook request",
            description = "Only set for a `multipart/form-data` request. The content of each part is stored in " +
                "Kestra's internal storage, and the part carries its URI."
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
         */
        @Builder
        @ToString
        @EqualsAndHashCode
        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        @Schema(title = "A file part of a `multipart/form-data` webhook request")
        public static class Part {
            @Schema(title = "The form field name of the part")
            @NotNull
            private String name;

            @Schema(title = "The name of the uploaded file")
            @NotNull
            private String filename;

            @Schema(
                title = "The content type of the part",
                description = "Not set if the caller did not send one for this part."
            )
            private String contentType;

            @Schema(title = "The size of the part content in bytes")
            @NotNull
            private Long size;

            @Schema(
                title = "The URI of the part content in Kestra's internal storage",
                description = "The content of the part is streamed to the internal storage as it is received, so " +
                    "that a file of any size reaches the flow intact and without travelling through the execution. " +
                    "It is stored under the execution the webhook call creates, and purged with it."
            )
            @NotNull
            private String uri;
        }
    }
}
