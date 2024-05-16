package io.kestra.plugin.core.http;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.runners.RunContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Make an HTTP API request to a specified URL and store the response as output.",
    description = """
                  This task makes an API call to a specified URL of an HTTP server and stores the response as output.
                  By default, the maximum length of the response is limited to 10MB, but it can be increased to at most 2GB by using the `options.maxContentLength` property.
                  Note that the response is added as output to the task. If you need to process large API payloads, we recommend using the `Download` task instead."""
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a Kestra flow via an HTTP POST request authenticated with basic auth. To pass a `user` input to the API call, we use the `formData` property. When using form data, make sure to set the `contentType` property to `multipart/form-data`.",
            full = true,
            code = """
            id: api_call
            namespace: dev
            tasks:
              - id: basic_auth_api
                type: io.kestra.plugin.core.http.Request
                uri: http://host.docker.internal:8080/api/v1/executions/dev/inputs_demo
                options:
                  basicAuthUser: admin
                  basicAuthPassword: admin
                method: POST
                contentType: multipart/form-data
                formData:
                  user: John Doe
            """
        ),
        @Example(
            title = "Send an HTTP POST request to a webserver",
            code = {
                "uri: \"https://server.com/login\"",
                "headers: ",
                "  user-agent: \"kestra-io\"",
                "method: \"POST\"",
                "formData:",
                "  user: \"user\"",
                "  password: \"pass\""
            }
        ),
        @Example(
            title = "Send a multipart HTTP POST request to a webserver",
            code = {
                "uri: \"https://server.com/upload\"",
                "headers: ",
                "  user-agent: \"kestra-io\"",
                "method: \"POST\"",
                "contentType: \"multipart/form-data\"",
                "formData:",
                "  user: \"{{ inputs.file }}\"",
            }
        ),
        @Example(
            title = "Send a multipart HTTP POST request to a webserver and set a custom file name",
            code = {
                "uri: \"https://server.com/upload\"",
                "headers: ",
                "  user-agent: \"kestra-io\"",
                "method: \"POST\"",
                "contentType: \"multipart/form-data\"",
                "formData:",
                "  user:",
                "    name: \"my-file.txt\"",
                "    content: \"{{ inputs.file }}\"",
            }
        )
    },
    aliases = "io.kestra.plugin.fs.http.Request"
)
public class Request extends AbstractHttp implements RunnableTask<Request.Output> {
    @Builder.Default
    @Schema(
        title = "If true, allow a failed response code (response code >= 400)"
    )
    private boolean allowFailed = false;

    @Builder.Default
    @Schema(
        title = "If true, the HTTP response body will be automatically encrypted and decrypted in the outputs, provided that encryption is configured in your Kestra configuration.",
        description = "If this property is set to `true`, this task will output the request body using the `encryptedBody` output property; otherwise, the request body will be stored in the `body` output property."
    )
    private boolean encryptBody = false;

    @SuppressWarnings("unchecked")
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        try (HttpClient client = this.client(runContext, this.method)) {
            HttpRequest<String> request = this.request(runContext);
            HttpResponse<String> response;

            try {
                response = client
                    .toBlocking()
                    .exchange(request, Argument.STRING, Argument.STRING);
            } catch (HttpClientResponseException e) {
                if (!allowFailed) {
                    throw e;
                }

                //noinspection unchecked
                response = (HttpResponse<String>) e.getResponse();
            }

            logger.debug("Request '{}' with the response code '{}'", request.getUri(), response.getStatus().getCode());

            return this.output(runContext, request, response);
        }
    }

    public Output output(RunContext runContext, HttpRequest<String> request, HttpResponse<String> response) throws GeneralSecurityException {
        response
            .getHeaders()
            .contentLength()
            .ifPresent(value -> {
                runContext.metric(Counter.of(
                    "response.length", value,
                    this.tags(request, response)
                ));
            });

        return Output.builder()
            .code(response.getStatus().getCode())
            .headers(response.getHeaders().asMap())
            .uri(request.getUri())
            .body(encryptBody ? null : response.body())
            .encryptedBody(encryptBody ? EncryptedString.from(response.body(), runContext) : null)
            .build();
    }

    @Builder(toBuilder = true)
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The URL of the current request"
        )
        private final URI uri;

        @Schema(
            title = "The status code of the response"
        )
        private final Integer code;

        @Schema(
            title = "The headers of the response"
        )
        @PluginProperty(additionalProperties = List.class)
        private final Map<String, List<String>> headers;

        @Schema(
            title = "The body of the response",
            description = "Kestra will by default store the task output using this property. However, if the `encryptBody` property is set to `true`, kestra will instead encrypt the output and store it using the `encryptedBody` output property."
        )
        private Object body;

        @Schema(
            title = "The encrypted body of the response",
            description = "If the `encryptBody` property is set to `true`, kestra will automatically encrypt the output before storing it, and decrypt it when the output is retrieved in a downstream task."
        )
        private EncryptedString encryptedBody;

        @Schema(
            title = "The form data to be sent in the request body",
            description = "When sending a file, you can pass a list of maps (i.e. a list of key-value pairs) with a key 'name' and value of the filename, as well as 'content' key with the file's content as value (e.g. passed from flow inputs or outputs from another task)."
        )
        @PluginProperty(dynamic = true)
        protected Map<String, Object> formData;
    }
}
