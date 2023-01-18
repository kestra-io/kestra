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
import io.micronaut.http.server.netty.types.files.NettyStreamedFileCustomizableResponseType;
import io.micronaut.http.server.netty.types.files.NettySystemFileCustomizableResponseType;
import org.apache.commons.io.IOUtils;
import org.reactivestreams.Publisher;

import java.io.IOException;

import java.util.Objects;
import io.micronaut.core.annotation.Nullable;

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
            .map(chain.proceed(request), response -> {
                boolean first = response.getBody(NettyStreamedFileCustomizableResponseType.class)
                    .filter(n -> n.getMediaType().getName().equals(MediaType.TEXT_HTML))
                    .isPresent();

                boolean second = response.getBody(NettySystemFileCustomizableResponseType.class)
                    .filter(n -> n.getFile().getAbsoluteFile().toString().endsWith("ui/index.html"))
                    .isPresent();

                if (first || second) {
                    try {
                        String content = IOUtils.toString(
                            Objects.requireNonNull(StaticFilter.class.getClassLoader().getResourceAsStream("ui/index.html")),
                            Charsets.UTF_8
                        );

                        String finalBody = replace(content);

                        return HttpResponse
                            .<String>ok()
                            .body(finalBody)
                            .contentType(MediaType.TEXT_HTML)
                            .contentLength(finalBody.length());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                return response;
            });
    }

    private String replace(String line) {
        line = line.replace("./", (basePath != null ? basePath : "") + "/ui/");

        if (googleAnalytics != null) {
            line = line.replace("KESTRA_GOOGLE_ANALYTICS = null;", "KESTRA_GOOGLE_ANALYTICS = '" + this.googleAnalytics + "';");
        }

        line = line.replace("<meta name=\"html-head\" content=\"replace\">", this.htmlHead == null ? "" : this.htmlHead);

        return line;
    }
}
