package io.kestra.plugin.core.http;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Download a file from a HTTP server.",
    description = "This task connects to a HTTP server and copy a file to Kestra's internal storage."
)
@Plugin(
    examples = {
        @Example(
            title = "Download a CSV file.",
            full = true,
            code = """
                id: download
                namespace: company.team

                tasks:
                  - id: extract
                    type: io.kestra.plugin.core.http.Download
                    uri: https://huggingface.co/datasets/kestra/datasets/raw/main/csv/orders.csv"""
        )
    },
    metrics = {
        @Metric(name = "response.length", type = "counter", description = "The content length")
    },
    aliases = "io.kestra.plugin.fs.http.Download"
)
public class Download extends AbstractHttp implements RunnableTask<Download.Output> {
    @Schema(title = "Should the task fail when downloading an empty file.")
    @Builder.Default
    @PluginProperty
    private final Boolean failOnEmptyResponse = true;

    @Builder.Default
    @Schema(
        title = "If true, allow a failed response code (response code >= 400)"
    )
    private boolean allowFailed = false;

    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        URI from = new URI(runContext.render(this.uri));

        File tempFile = runContext.workingDir().createTempFile(filenameFromURI(from)).toFile();

        // output
        Output.OutputBuilder builder = Output.builder();

        // do it
        try (
            ReactorStreamingHttpClient client = this.streamingClient(runContext, this.method);
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile));
        ) {
            @SuppressWarnings("unchecked")
            HttpRequest<String> request = this.request(runContext);
            Long size;

            try {
                size = client
                    .exchangeStream(request)
                    .map(throwFunction(response -> {
                        if (builder.code == null) {
                            builder
                                .code(response.code())
                                .headers(response.getHeaders().asMap());
                        }

                        if (response.getBody().isPresent()) {
                            byte[] bytes = response.getBody().get().toByteArray();
                            output.write(bytes);

                            return (long) bytes.length;
                        } else {
                            return 0L;
                        }
                    }))
                    .reduce(Long::sum)
                    .block();
            } catch (HttpClientResponseException e) {
                if (!allowFailed) {
                    throw e;
                } else {
                    builder
                        .headers(e.getResponse().getHeaders().asMap())
                        .code(e.getResponse().getStatus().getCode());

                    size = e.getResponse().getContentLength();
                }
            }


            if (size == null) {
                size = 0L;
            }

            if (builder.headers != null && builder.headers.containsKey("Content-Length")) {
                long length = Long.parseLong(builder.headers.get("Content-Length").getFirst());
                if (length != size) {
                    throw new IllegalStateException("Invalid size, got " + size + ", expected " + length);
                }
            }

            output.flush();

            runContext.metric(Counter.of("response.length", size, this.tags(request, null)));
            builder.length(size);

            if (size == 0) {
                if (this.failOnEmptyResponse && !this.allowFailed) {
                    throw new HttpClientResponseException("No response from server", HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE));
                } else {
                    logger.warn("File '{}' is empty", from);
                }
            }

            String filename = null;
            if (builder.headers != null && builder.headers.containsKey("Content-Disposition")) {
                String contentDisposition = builder.headers.get("Content-Disposition").getFirst();
                filename = filenameFromHeader(runContext, contentDisposition);
            }
            if (filename != null) {
                filename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            }

            builder.uri(runContext.storage().putFile(tempFile, filename));

            logger.debug("File '{}' downloaded to '{}'", from, builder.uri);

            return builder.build();
        }
    }

    // Note: this is a basic implementation that should cover all possible use cases.
    // If this is not enough, we should find some helper method somewhere to cover all possible rules of the Content-Disposition header.
    private String filenameFromHeader(RunContext runContext, String contentDisposition) {
        try {
            // Content-Disposition parts are separated by ';'
            String[] parts = contentDisposition.split(";");
            String filename = null;
            for (String part : parts) {
                String stripped = part.strip();
                if (stripped.startsWith("filename")) {
                    filename = stripped.substring(stripped.lastIndexOf('=') + 1);
                }
                if (stripped.startsWith("filename*")) {
                    // following https://datatracker.ietf.org/doc/html/rfc5987 the filename* should be <ENCODING>'(lang)'<filename>
                    filename = stripped.substring(stripped.lastIndexOf('\'') + 2, stripped.length() - 1);
                }
            }
            // filename may be in double-quotes
            if (filename != null && filename.charAt(0) == '"') {
                filename = filename.substring(1, filename.length() - 1);
            }
            // if filename contains a path: use only the last part to avoid security issues due to host file overwriting
            if (filename != null && filename.contains(File.separator)) {
                filename = filename.substring(filename.lastIndexOf(File.separator) + 1);
            }
            return filename;
        } catch (Exception e) {
            // if we cannot parse the Content-Disposition header, we return null
            runContext.logger().debug("Unable to parse the Content-Disposition header: {}", contentDisposition, e);
            return null;
        }
    }

    private String filenameFromURI(URI uri) {
        String path = uri.getPath();
        if (path == null) {
            return null;
        }

        if (path.indexOf('/') != -1) {
            path = path.substring(path.lastIndexOf('/')); // keep the last segment
        }
        if (path.indexOf('.') != -1) {
            return path.substring(path.indexOf('.'));
        }
        return null;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The URL of the downloaded file on Kestra's internal storage."
        )
        private final URI uri;

        @Schema(
            title = "The status code of the response."
        )
        private final Integer code;

        @Schema(
                title = "The content-length of the response."
        )
        private final Long length;

        @Schema(
            title = "The headers of the response."
        )
        private final Map<String, List<String>> headers;
    }
}
