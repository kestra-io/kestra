package io.kestra.plugin.core.http;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

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
                namespace: company.team

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
            title = "Execute a Kestra flow via an HTTP request authenticated with a Bearer auth token / JWT token.",
            full = true,
            code = """
                id: jwt_auth_call
                namespace: company.team

                tasks:
                  - id: auth_token_api
                    type: io.kestra.plugin.core.http.Request
                    uri: https://dummyjson.com/user/me
                    method: GET
                    headers:
                      Authorization: 'Bearer <TOKEN>'
                """
        ),
        @Example(
            title = "Execute a Kestra flow via an HTTP request authenticated with API key passed in the header.",
            full = true,
            code = """
                id: api_key_auth_call
                namespace: company.team

                tasks:
                  - id: api_key_auth
                    type: io.kestra.plugin.core.http.Request
                    uri: https://dummyjson.com/user/me
                    method: GET
                    headers:
                      X-API-KEY: abcde12345
                """
        ),
        @Example(
            title = "Execute a Kestra flow via an HTTP request authenticated with API key passed in the query parameters.",
            full = true,
            code = """
                id: api_key_auth_call
                namespace: company.team

                tasks:
                  - id: api_key_in_query_params
                    type: io.kestra.plugin.core.http.Request
                    uri: "https://dummyjson.com/user/me?api_key={{ secret('API_KEY') }}"
                    method: GET
                """
        ),
        @Example(
            title = "Make an HTTP GET request with a timeout. The `timeout` property specifies the maximum time allowed for the entire task to run, while the `options.connectTimeout`, `options.readTimeout`, `options.connectionPoolIdleTimeout`, and `options.readIdleTimeout` properties specify the time allowed for establishing a connection, reading data from the server, keeping an idle connection in the client's connection pool, and keeping a read connection idle before closing it, respectively.",
            full = true,
            code = """
                id: timeout
                namespace: company.team

                tasks:
                  - id: http
                    type: io.kestra.plugin.core.http.Request
                    uri: https://reqres.in/api/long-request
                    timeout: PT10M # no default
                    method: GET
                    options:
                      connectTimeout: PT1M # no default
                      readTimeout: PT30S # 10 seconds by default
                      connectionPoolIdleTimeout: PT10S # 0 seconds by default
                      readIdleTimeout: PT10M # 300 seconds by default
                """
        ),
        @Example(
            title = "Make a HTTP request and process its output. Given that we send a JSON payload in the request body, we need to use `application/json` as content type.",
            full = true,
            code = """
                id: http_post_request_example
                namespace: company.team

                inputs:
                  - id: payload
                    type: JSON
                    defaults: |
                      {"title": "Kestra Pen"}

                tasks:
                  - id: send_data
                    type: io.kestra.plugin.core.http.Request
                    uri: https://dummyjson.com/products/add
                    method: POST
                    contentType: application/json
                    body: "{{ inputs.payload }}"

                  - id: print_status
                    type: io.kestra.plugin.core.log.Log
                    message: '{{ outputs.send_data.body }}'
                """
        ),
        @Example(
            title = "Send an HTTP POST request to a webserver.",
            full = true,
            code = """
                id: http_post_request_example
                namespace: company.team

                tasks:
                  - id: send_data
                    type: io.kestra.plugin.core.http.Request
                    uri: "https://server.com/login"
                    headers:
                      user-agent: "kestra-io"
                    method: "POST"
                    formData:
                      user: "user"
                      password: "pass"
                """
        ),
        @Example(
            title = "Send a multipart HTTP POST request to a webserver.",
            full = true,
            code = """
                id: http_post_multipart_example
                namespace: company.team

                inputs:
                  - id: file
                    type: FILE

                tasks:
                  - id: send_data
                    type: io.kestra.plugin.core.http.Request
                    uri: "https://server.com/upload"
                    headers:
                      user-agent: "kestra-io"
                    method: "POST"
                    contentType: "multipart/form-data"
                    formData:
                      user: "{{ inputs.file }}"
                """
        ),
        @Example(
            title = "Send a multipart HTTP POST request to a webserver and set a custom file name.",
            full = true,
            code = """
                id: http_post_multipart_example
                namespace: company.team

                inputs:
                  - id: file
                    type: FILE

                tasks:
                  - id: send_data
                    type: io.kestra.plugin.core.http.Request
                    uri: "https://server.com/upload"
                    headers:
                      user-agent: "kestra-io"
                    method: "POST"
                    contentType: "multipart/form-data"
                    formData:
                      user:
                        name: "my-file.txt"
                        content: "{{ inputs.file }}"
                """
        ),
        @Example(
            title = "Upload an image using HTTP POST request to a webserver.",
            full = true,
            code = """
                id: http_upload_image
                namespace: company.team

                tasks:
                  - id: s3_download
                    type: io.kestra.plugin.aws.s3.Download
                    accessKeyId: "{{ secret('AWS_ACCESS_KEY_ID')}}"
                    secretKeyId: "{{ secret('AWS_SECRET_KEY_ID')}}"
                    region: "eu-central-1"
                    bucket: "my-bucket"
                    key: "path/to/file/my_image.jpeg"

                  - id: send_data
                    type: io.kestra.plugin.core.http.Request
                    uri: "https://server.com/upload"
                    headers:
                      user-agent: "kestra-io"
                    method: "POST"
                    contentType: "image/jpeg"
                    formData:
                      user:
                        file: "my-image.jpeg"
                        url: "{{ outputs.s3_download.uri }}"
                        metadata:
                          description: "my favorite image"
                """
        ),
        @Example(
            title = "Upload a CSV file using HTTP POST request to a webserver.",
            full = true,
            code = """
                id: http_csv_file_upload
                namespace: company.team

                tasks:
                  - id: http_download
                    type: io.kestra.plugin.core.http.Download
                    uri: https://huggingface.co/datasets/kestra/datasets/raw/main/csv/orders.csv

                  - id: upload
                    type: io.kestra.plugin.core.http.Request
                    uri: "https://server.com/upload"
                    headers:
                      user-agent: "kestra-io"
                    method: "POST"
                    contentType: "multipart/form-data"
                    formData:
                      url: "{{ outputs.http_download.uri }}"
                """
        )
    },
    aliases = "io.kestra.plugin.fs.http.Request"
)
public class Request extends AbstractHttp implements RunnableTask<Request.Output> {
    @Builder.Default
    @Schema(
        title = "If true, the HTTP response body will be automatically encrypted and decrypted in the outputs, provided that encryption is configured in your Kestra configuration.",
        description = "If this property is set to `true`, this task will output the request body using the `encryptedBody` output property; otherwise, the request body will be stored in the `body` output property."
    )
    private Property<Boolean> encryptBody = Property.of(false);

    public Output run(RunContext runContext) throws Exception {
        try (HttpClient client = this.client(runContext)) {
            HttpRequest request = this.request(runContext);

            HttpResponse<Byte[]> response = client.request(request, Byte[].class);

            String body = null;

            if (response.getBody() != null) {
                body = IOUtils.toString(ArrayUtils.toPrimitive(response.getBody()), StandardCharsets.UTF_8.name());
            }

            // check that the string is a valid Unicode string
            if (body != null) {
                OptionalInt illegalChar = body.chars().filter(c -> !Character.isDefined(c)).findFirst();
                if (illegalChar.isPresent()) {
                    throw new IllegalArgumentException("Illegal unicode code point in request body: " + illegalChar.getAsInt() +
                        ", the Request task only support valid Unicode strings as body.\n" +
                        "You can try using the Download task instead.");
                }
            }

            return this.output(runContext, request, response, body);
        }
    }

    public Output output(RunContext runContext, HttpRequest request, HttpResponse<Byte[]> response, String body) throws GeneralSecurityException, URISyntaxException, IOException, IllegalVariableEvaluationException {
        boolean encrypt = runContext.render(this.encryptBody).as(Boolean.class).orElseThrow();
        return Output.builder()
            .code(response.getStatus().getCode())
            .headers(response.getHeaders().map())
            .uri(request.getUri())
            .body(encrypt ? null : body)
            .encryptedBody(encrypt ? EncryptedString.from(body, runContext) : null)
            .build();
    }

    @Builder(toBuilder = true)
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The URL of the current request."
        )
        private final URI uri;

        @Schema(
            title = "The status code of the response."
        )
        private final Integer code;

        @Schema(
            title = "The headers of the response."
        )
        @PluginProperty(additionalProperties = List.class)
        private final Map<String, List<String>> headers;

        @Schema(
            title = "The body of the response.",
            description = "Kestra will by default store the task output using this property. However, if the `encryptBody` property is set to `true`, kestra will instead encrypt the output and store it using the `encryptedBody` output property."
        )
        private Object body;

        @Schema(
            title = "The encrypted body of the response.",
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
