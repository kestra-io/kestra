package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
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
    @Value("${kestra.webserver.html-title}")
    protected String htmlTitle;

    @Nullable
    @Value("${kestra.webserver.html-head}")
    protected String htmlHead;

    private volatile String indexHtml;

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return Publishers
            .map(chain.proceed(request), (MutableHttpResponse<?> response) ->
            {
                try {
                    Optional<? extends MutableHttpResponse<?>> alteredResponse = Stream
                        .of(
                            // jar mode
                            response.getBody(StreamedFile.class)
                                .filter(n -> n.getMediaType().getName().equals(MediaType.TEXT_HTML))
                                .map(throwFunction(n ->
                                {
                                    try (InputStream in = n.getInputStream()) {
                                        return indexHtml(in);
                                    }
                                })),
                            // debug mode
                            response.getBody(SystemFile.class)
                                .filter(n -> n.getFile().getAbsoluteFile().toString().endsWith("ui/index.html"))
                                .map(throwFunction(n ->
                                {
                                    try (InputStream in = Objects.requireNonNull(StaticFilter.class.getClassLoader().getResourceAsStream("ui/index.html"))) {
                                        return indexHtml(in);
                                    }
                                }))
                        )
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(finalBody ->
                        {
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

    // index.html never changes at runtime: inflate it from the jar once, not on every /ui/** request
    private String indexHtml(InputStream in) throws IOException {
        String html = indexHtml;
        if (html == null) {
            html = replace(IOUtils.toString(in, StandardCharsets.UTF_8));
            indexHtml = html;
        }
        return html;
    }

    private String replace(String line) {
        if (!line.contains("KESTRA_UI_PATH")) {
            return line;
        }

        line = line.replace("./", (basePath != null ? basePath : "") + "/ui/");

        if (googleAnalytics != null) {
            line = line.replace("KESTRA_GOOGLE_ANALYTICS = null;", "KESTRA_GOOGLE_ANALYTICS = '" + this.googleAnalytics + "';");
        }

        if (htmlTitle != null) {
            line = line.replaceFirst("<title>(.*)</title>", "<title>" + this.htmlTitle + "</title>");
        }

        line = line.replace("<meta name=\"html-head\" content=\"replace\">", this.htmlHead == null ? "" : this.htmlHead);

        return line;
    }
}
