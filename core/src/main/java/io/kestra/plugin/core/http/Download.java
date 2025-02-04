package io.kestra.plugin.core.http;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.utils.Rethrow.throwConsumer;

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
    private final Property<Boolean> failOnEmptyResponse = Property.of(true);

    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        URI from = new URI(runContext.render(this.uri).as(String.class).orElseThrow());

        File tempFile = runContext.workingDir().createTempFile(filenameFromURI(from)).toFile();

        try (
            HttpClient client = this.client(runContext);
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile));
        ) {
            HttpRequest request = this.request(runContext);
            AtomicReference<Long> size = new AtomicReference<>();

            HttpResponse<Void> response = client.request(
                request,
                throwConsumer(r -> {
                    if (r.getBody() != null) {
                        size.set(IOUtils.copyLarge(r.getBody(), output));
                    }

                    if (size.get() == null) {
                        size.set(0L);
                    }

                    if (r.getBody() != null) {
                        r.getHeaders().firstValue("Content-Length").ifPresent(header -> {
                            long length = Long.parseLong(header);

                            if (length != size.get()) {
                                throw new IllegalStateException("Invalid size, got " + size + ", expected " + length);
                            }
                        });
                    }

                    output.flush();
                })
            );

            if (size.get() == 0) {
                if (runContext.render(this.failOnEmptyResponse).as(Boolean.class).orElseThrow()) {
                    boolean allowFailed = this.options != null && runContext.render(this.options.getAllowFailed()).as(Boolean.class).orElseThrow();
                    if (!allowFailed) {
                        throw new HttpClientResponseException("No response from server", response);
                    }
                } else {
                    logger.warn("File '{}' is empty", from);
                }
            }

            String filename = null;
            if (response.getHeaders().firstValue("Content-Disposition").isPresent()) {
                String contentDisposition = response.getHeaders().firstValue("Content-Disposition").orElseThrow();
                filename = filenameFromHeader(runContext, contentDisposition);
            }
            if (filename != null) {
                filename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            }

            logger.debug("File '{}' downloaded with size '{}'", from, size);

            return Output.builder()
                .code(response.getStatus().getCode())
                .uri(runContext.storage().putFile(tempFile, filename))
                .headers(response.getHeaders().map())
                .length(size.get())
                .build();
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
