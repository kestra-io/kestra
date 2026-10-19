package io.kestra.plugin.core.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.net.URIBuilder;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpRedactor;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractHttp extends Task implements HttpInterface {
    @NotNull
    protected Property<String> uri;

    @Builder.Default
    protected Property<String> method = Property.ofValue("GET");

    protected Property<String> body;

    protected Property<Map<String, Object>> formData;

    protected Property<Map<String, Object>> params;

    @Builder.Default
    protected Property<String> contentType = Property.ofValue("application/json");

    protected Property<Map<CharSequence, CharSequence>> headers;

    @Builder.Default
    protected HttpConfiguration options = HttpConfiguration.builder().build();

    @Builder.Default
    @Schema(
        title = "Whether to output a summary of the request that was sent.",
        description = "When `true`, the `request` output describes the rendered request (method, redacted URI, header and parameter names, body size, authentication type). Header values, query values and body content are never included."
    )
    protected Property<Boolean> outputRequest = Property.ofValue(true);

    protected HttpClient client(RunContext runContext) throws IllegalVariableEvaluationException, MalformedURLException, URISyntaxException {
        return HttpClient.builder()
            .configuration(this.options)
            .runContext(runContext)
            .build();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected HttpRequest request(RunContext runContext) throws IllegalVariableEvaluationException, URISyntaxException, IOException {
        // ideally we should URLEncode the path of the UI, but as we cannot URLEncode everything, we handle the common case of space in the URI.
        String renderedUri = runContext.render(this.uri).as(String.class).map(s -> s.replace(" ", "%20")).orElseThrow();

        URIBuilder uriBuilder = new URIBuilder(renderedUri);

        if (this.params != null) {
            runContext
                .render(this.params)
                .asMap(String.class, Object.class)
                .forEach((s, o) ->
                {
                    if (o instanceof List<?> oList) {
                        oList.stream().map(Object::toString).forEach(s1 ->
                        {
                            uriBuilder.addParameter(s, s1);
                        });
                    } else if (o instanceof String oString) {
                        uriBuilder.addParameter(s, oString);
                    } else {
                        throw new IllegalArgumentException("Unsupported param type: " + o.getClass());
                    }
                });
        }

        HttpRequest.HttpRequestBuilder request = HttpRequest.builder()
            .method(runContext.render(this.method).as(String.class).orElse(null))
            .uri(uriBuilder.build());

        var renderedFormData = runContext.render(this.formData).asMap(String.class, Object.class);
        if (!renderedFormData.isEmpty()) {
            if ("multipart/form-data".equals(runContext.render(this.contentType).as(String.class).orElse(null))) {
                HashMap<String, Object> multipart = new HashMap<>();

                for (Map.Entry<String, Object> e : renderedFormData.entrySet()) {
                    String key = runContext.render(e.getKey());

                    if (e.getValue() instanceof String stringValue) {
                        String render = runContext.render(stringValue);

                        if (render.startsWith("kestra://")) {
                            File tempFile = runContext.workingDir().createTempFile().toFile();

                            try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                                IOUtils.copyLarge(runContext.storage().getFile(new URI(render)), outputStream);
                            }

                            multipart.put(key, tempFile);
                        } else {
                            multipart.put(key, render);
                        }
                    } else if (e.getValue() instanceof Map mapValue && ((Map<String, String>) mapValue).containsKey("name") && ((Map<String, String>) mapValue).containsKey("content")) {
                        String name = runContext.render(((Map<String, String>) mapValue).get("name"));
                        String content = runContext.render(((Map<String, String>) mapValue).get("content"));

                        File tempFile = runContext.workingDir().createTempFile().toFile();
                        File renamedFile = new File(Files.move(tempFile.toPath(), tempFile.toPath().resolveSibling(name)).toUri());

                        try (OutputStream outputStream = new FileOutputStream(renamedFile)) {
                            IOUtils.copyLarge(runContext.storage().getFile(new URI(content)), outputStream);
                        }

                        multipart.put(key, renamedFile);
                    } else {
                        multipart.put(key, JacksonMapper.ofJson().writeValueAsString(e.getValue()));
                    }
                }

                request.body(HttpRequest.MultipartRequestBody.builder().content(multipart).build());
            } else {
                request.body(
                    HttpRequest.UrlEncodedRequestBody.builder()
                        .content(renderedFormData)
                        .charset(defaultCharset(runContext))
                        .build()
                );
            }
        } else if (this.body != null) {
            request.body(
                HttpRequest.StringRequestBody.builder()
                    .content(runContext.render(body).as(String.class).orElseThrow())
                    .contentType(runContext.render(this.contentType).as(String.class).orElse(null))
                    .charset(defaultCharset(runContext))
                    .build()
            );
        } else if (this.contentType != null) {
            request.addHeader("Content-Type", runContext.render(this.contentType).as(String.class).orElse(null));
        }

        var renderedHeader = runContext.render(this.headers).asMap(CharSequence.class, CharSequence.class);
        if (!renderedHeader.isEmpty()) {
            request.headers(
                HttpHeaders.of(
                    renderedHeader
                        .entrySet()
                        .stream()
                        .map(
                            throwFunction(
                                e -> new AbstractMap.SimpleEntry<>(
                                    e.getKey().toString(),
                                    runContext.render(e.getValue().toString())
                                )
                            )
                        )
                        .collect(Collectors.groupingBy(AbstractMap.SimpleEntry::getKey, Collectors.mapping(AbstractMap.SimpleEntry::getValue, Collectors.toList()))),
                    (a, b) -> true
                )
            );
        }

        return request.build();
    }

    protected RequestSummary requestSummary(RunContext runContext, HttpRequest request) throws IllegalVariableEvaluationException, IOException {
        if (!runContext.render(this.outputRequest).as(Boolean.class).orElse(true)) {
            return null;
        }

        Duration timeout = this.options != null && this.options.getTimeout() != null
            ? runContext.render(this.options.getTimeout().getReadIdleTimeout()).as(Duration.class).orElse(null)
            : null;
        Boolean followRedirects = this.options != null ? runContext.render(this.options.getFollowRedirects()).as(Boolean.class).orElse(null) : null;

        return RequestSummary.of(request, this.options, timeout, followRedirects, runContext.usedSecrets());
    }

    protected ResponseSummary responseSummary(RunContext runContext, HttpRequest request, HttpResponse<?> response, long contentLength, Duration elapsed) {
        ResponseSummary summary = ResponseSummary.of(response, request.getUri(), contentLength, elapsed.toMillis(), runContext.usedSecrets());

        runContext.metric(Timer.of("response.time", elapsed));
        runContext.metric(Counter.of("retries", summary.retries()));
        runContext.metric(Counter.of("response.length", contentLength));

        runContext.logger().debug(
            "{} {} responded with status code '{}' in {} ms after {} retries",
            request.getMethod(),
            HttpRedactor.redactUri(request.getUri(), runContext.usedSecrets()),
            response.getStatus().getCode(),
            summary.durationMs(),
            summary.retries()
        );

        return summary;
    }

    protected Map<String, List<String>> redactedHeaders(RunContext runContext, HttpResponse<?> response) {
        return HttpRedactor.redactHeaders(response.getHeaders().map(), runContext.usedSecrets());
    }

    private Charset defaultCharset(RunContext runContext) throws IllegalVariableEvaluationException {
        return this.options != null && this.options.getDefaultCharset() != null
            ? runContext.render(this.options.getDefaultCharset()).as(Charset.class).orElse(null)
            : null;
    }
}
