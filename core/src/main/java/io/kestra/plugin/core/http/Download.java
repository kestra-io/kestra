package io.kestra.plugin.core.http;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TypeConverter;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Download a file over HTTP(S) to Kestra storage.",
    description = """
        Performs an HTTP request and streams the response body into internal storage. Validates Content-Length when present and can fail on empty responses (`failOnEmptyResponse`, unless `options.allowFailed` allows it). Filename is taken from `saveAs`, `Content-Disposition`, or derived from the URI.

        By default the task also outputs a `request` summary and response metadata such as `durationMs` and `retries`. Set `outputRequest` to `false` to drop the `request` summary. Sensitive data is redacted from the outputs: see the `headers` and `request` outputs."""
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
        @Metric(name = "response.time", type = "timer", description = "The duration of the HTTP call, including retries."),
        @Metric(name = "retries", type = "counter", description = "The number of retries performed."),
        @Metric(name = "response.length", type = "counter", description = "The content length")
    },
    aliases = "io.kestra.plugin.fs.http.Download"
)
public class Download extends AbstractHttp implements RunnableTask<Download.Output> {
    @Schema(title = "Should the task fail when downloading an empty file.")
    @Builder.Default
    private Property<Boolean> failOnEmptyResponse = Property.ofValue(true);

    @Schema(
        title = "Name of the file inside the output.",
        description = """
            If not provided, the filename will be extracted from the `Content-Disposition` header.
            If no `Content-Disposition` header, a name would be generated."""
    )
    private Property<String> saveAs;

    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        URI from = new URI(runContext.render(this.uri).as(String.class).orElseThrow());

        File tempFile = runContext.workingDir().createTempFile(filenameFromURI(from)).toFile();

        try (
            HttpClient client = this.client(runContext);
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile));
        ) {
            HttpRequest request = this.request(runContext);
            RequestSummary requestSummary = this.requestSummary(runContext, request);
            AtomicReference<Long> size = new AtomicReference<>();

            long start = System.nanoTime();
            HttpResponse<Void> response = client.request(
                request,
                throwConsumer(r ->
                {
                    if (r.getBody() != null) {
                        size.set(IOUtils.copyLarge(r.getBody(), output));
                    }

                    if (size.get() == null) {
                        size.set(0L);
                    }

                    // Content-Length describes the size of the transferred (encoded) body, while `size` counts the
                    // bytes actually written after any content-coding has been transparently decoded (e.g. gzip).
                    // The two only match for identity encoding, so skip the check when the response is content-encoded.
                    boolean contentEncoded = r.getHeaders().firstValue("Content-Encoding")
                        .map(encoding -> !encoding.isBlank() && !"identity".equalsIgnoreCase(encoding))
                        .orElse(false);

                    if (r.getBody() != null && !contentEncoded) {
                        r.getHeaders().firstValue("Content-Length").ifPresent(header ->
                        {
                            long length = TypeConverter.toLong(header);

                            if (length != size.get()) {
                                throw new IllegalStateException("Invalid size, got " + size + ", expected " + length);
                            }
                        });
                    }

                    output.flush();
                })
            );
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

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

            String rFilename = runContext.render(this.saveAs).as(String.class).orElse(null);
            if (rFilename == null) {
                if (response.getHeaders().firstValue("Content-Disposition").isPresent()) {
                    String contentDisposition = response.getHeaders().firstValue("Content-Disposition").orElseThrow();
                    rFilename = filenameFromHeader(runContext, contentDisposition);
                    if (rFilename != null) {
                        // Spaces are encoded as '+' to keep backward compatibility with the existing file naming convention.
                        // All other URI-special characters (e.g. '[', ']', '#', '%') are handled by InternalStorage.buildStorageUri
                        // via the quoting URI constructor, which percent-encodes them without double-encoding.
                        rFilename = rFilename.replace(' ', '+');
                    }
                }
            }

            logger.debug("File '{}' downloaded with size '{}'", from, size);

            ResponseSummary summary = this.responseSummary(runContext, request, response, size.get(), elapsed);

            return Output.builder()
                .code(response.getStatus().getCode())
                .reason(summary.reason())
                .uri(runContext.storage().putFile(tempFile, rFilename))
                .headers(this.redactedHeaders(runContext, response))
                .length(size.get())
                .request(requestSummary)
                .contentType(summary.contentType())
                .contentLength(summary.contentLength())
                .durationMs(summary.durationMs())
                .finalUri(summary.finalUri())
                .redirects(summary.redirects())
                .protocol(summary.protocol())
                .retries(summary.retries())
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
        if (path.lastIndexOf('.') != -1) {
            return path.substring(path.lastIndexOf('.'));
        }
        return null;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The URI of the downloaded file in Kestra's internal storage.",
            description = "This is an internal Kestra storage URI (e.g. `kestra:///namespace/flow/executions/.../filename`), not an HTTP URL. " +
                "The actual storage backend (local filesystem, S3, GCS, Azure Blob, etc.) is determined by your Kestra configuration. " +
                "Pass this URI to subsequent tasks using `{{ outputs.<task_id>.uri }}`."
        )
        private final URI uri;

        @Schema(
            title = "The status code of the response"
        )
        private final Integer code;

        @Schema(
            title = "The size of the downloaded file in bytes",
            description = "Same value as `contentLength`."
        )
        private final Long length;

        @Schema(
            title = "The headers of the response",
            description = "A header value is replaced with `***` when the header name is on the deny-list (`Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key`, `X-Auth-Token`, `X-Amz-Security-Token`, `X-Amz-Signature`, `Ocp-Apim-Subscription-Key`), when the name contains `token`, `secret`, `password`, `credential`, `signature` or `api-key` (any case), when a whole word of the name is `key`, `sig`, `auth`, `pass` or `pwd` (so `page`, `monkey` and `keyword` are kept, and `Idempotency-Key` and `Sec-WebSocket-Key` are never redacted by name), or when the value is exactly a `secret()` resolved in the task. The `Location`, `Content-Location` and `Link` values are redacted as URIs: user-info is removed and only credential query or fragment parameters (`access_token`, `id_token`, `refresh_token`, `token`, `api_key`, `apikey`, `key`, `sig`, `signature`, `password`, `secret`, `auth`, `client_secret`, `code`, `X-Amz-Signature`, `X-Amz-Credential`, `X-Amz-Security-Token`) are masked, so pagination cursors such as `page_token` are kept. The `Access-Control-Allow-Credentials`, `Access-Control-Allow-Headers`, `Access-Control-Expose-Headers` and `Access-Control-Request-Headers` headers are never redacted by name."
        )
        private final Map<String, List<String>> headers;

        @Schema(
            title = "The reason phrase of the response status",
            description = "For example `OK` or `Service Unavailable`."
        )
        private final String reason;

        @Schema(
            title = "The summary of the request that was sent",
            description = "Only present when `outputRequest` is `true`. It holds the method, the redacted URI, the host and path, the names of query parameters, headers and form fields, the body size, the authentication type and the timeout. It never holds header values, query values or body content."
        )
        private final RequestSummary request;

        @Schema(
            title = "The content type of the response",
            description = "The value of the `Content-Type` response header, or `null` if the server did not send one."
        )
        private final String contentType;

        @Schema(
            title = "The size of the downloaded file in bytes",
            description = "The number of bytes written to internal storage, after any content-coding is decoded. Same value as `length`, and not the `Content-Length` header. `0` when the response has no body."
        )
        private final Long contentLength;

        @Schema(
            title = "The duration of the call in milliseconds",
            description = "Measured around the client call, so it includes retries and the time to download the body."
        )
        private final Long durationMs;

        @Schema(
            title = "The final URI of the response",
            description = "The URI reached after following redirects. The user-info is removed. Query and fragment values are replaced with `***` when the parameter name is sensitive (same name rules as `headers`, including the whole words `key`, `sig`, `auth`, `pass` and `pwd`) or the value is a resolved `secret()`. Path segments, or a run of consecutive segments, that are a resolved `secret()` are replaced with `***`, and a URI that is entirely a secret keeps only its scheme and host. Within the `Location` redirects, only credential parameters are masked, as described in `headers`."
        )
        private final String finalUri;

        @Schema(
            title = "The redirects followed to reach the response",
            description = "Each entry has the `code` of the redirect response and the `location` it pointed to, redacted like `finalUri`, with the same narrower credential-parameter rule as the `Location` header so pagination cursors are kept. Empty when no redirect was followed."
        )
        private final List<HttpResponse.Redirect> redirects;

        @Schema(
            title = "The HTTP protocol version of the response",
            description = "For example `HTTP/1.1`."
        )
        private final String protocol;

        @Schema(
            title = "The number of retries performed",
            description = "Counts only the retries of the Kestra retry policy configured in `options.retry`. Redirects and retries done by the underlying HTTP stack are not counted. `0` when the first attempt succeeded."
        )
        private final Integer retries;
    }
}
