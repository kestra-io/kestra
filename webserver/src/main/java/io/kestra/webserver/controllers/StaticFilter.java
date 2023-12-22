package io.kestra.webserver.controllers;

import com.google.common.base.Charsets;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import org.apache.commons.io.IOUtils;
import org.reactivestreams.Publisher;

import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import io.micronaut.core.annotation.Nullable;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Filter("/ui/**")
public class StaticFilter implements HttpServerFilter {
    @Nullable
    @Value("${micronaut.server.context-path}")
    protected String basePath;

    @Nullable
    @Value("${kestra.webserver.google-analytics}")
    protected String googleAnalytics;

    @Nullable
    @Value("${kestra.webserver.html-head}")
    protected String htmlHead;

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return Publishers
            .map(chain.proceed(request), (MutableHttpResponse<?> response) -> {
                try {
                    Optional<? extends MutableHttpResponse<?>> alteredResponse = Stream
                        .of(
                            // jar mode
                            response.getBody(StreamedFile.class)
                                .filter(n -> n.getMediaType().getName().equals(MediaType.TEXT_HTML))
                                .map(throwFunction(n -> IOUtils.toString(n.getInputStream(), StandardCharsets.UTF_8))),
                            // debug mode
                            response.getBody(SystemFile.class)
                                .filter(n -> n.getFile().getAbsoluteFile().toString().endsWith("ui/index.html"))
                                .map(throwFunction(n -> IOUtils.toString(
                                    Objects.requireNonNull(StaticFilter.class.getClassLoader().getResourceAsStream("ui/index.html")),
                                    Charsets.UTF_8
                                )))
                        )
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(s -> {
                            String finalBody = replace(s);

                            return (MutableHttpResponse<?>) HttpResponse
                                .ok()
                                .body(finalBody)
                                .contentType(MediaType.TEXT_HTML)
                                .contentLength(finalBody.length());
                        })
                        .findFirst();

                    return alteredResponse.isPresent() ? alteredResponse.get() : response;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    private String replace(String line) {
        if (!line.contains("KESTRA_UI_PATH")) {
            return line;
        }

        line = line.replace("./", (basePath != null ? basePath : "") + "/ui/");

        if (googleAnalytics != null) {
            line = line.replace("KESTRA_GOOGLE_ANALYTICS = null;", "KESTRA_GOOGLE_ANALYTICS = '" + this.googleAnalytics + "';");
        }

        line = line.replace("<meta name=\"html-head\" content=\"replace\">", this.htmlHead == null ? "" : this.htmlHead);

        return line;
    }
}
