package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.reactivestreams.Publisher;

import io.kestra.webserver.configuration.WebserverConfiguration;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.SameSite;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.security.csrf.CsrfConfiguration;
import io.micronaut.security.csrf.generator.CsrfTokenGenerator;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Filter("/ui/**")
public class StaticFilter implements HttpServerFilter {
    @Nullable
    @Value("${micronaut.server.context-path}")
    protected String basePath;

    @Inject
    protected WebserverConfiguration webserverConfiguration;

    @Inject
    protected Optional<CsrfConfiguration> csrfConfiguration;

    @Inject
    protected Optional<CsrfTokenGenerator<HttpRequest<?>>> csrfTokenGenerator;

    private static final Pattern HTML_PATTERN = Pattern.compile("<html.*?>", Pattern.CASE_INSENSITIVE);

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
                                .map(throwFunction(n -> IOUtils.toString(n.getInputStream(), StandardCharsets.UTF_8))),
                            // debug mode
                            response.getBody(SystemFile.class)
                                .filter(n -> n.getFile().getAbsoluteFile().toString().endsWith("ui/index.html"))
                                .map(
                                    throwFunction(
                                        n -> IOUtils.toString(
                                            Objects.requireNonNull(StaticFilter.class.getClassLoader().getResourceAsStream("ui/index.html")),
                                            StandardCharsets.UTF_8
                                        )
                                    )
                                )
                        )
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(s ->
                        {
                            String finalBody = replace(s);

                            return (MutableHttpResponse<?>) HttpResponse
                                .ok()
                                .body(finalBody)
                                .contentType(MediaType.TEXT_HTML)
                                .contentLength(finalBody.length());
                        })
                        .findFirst();

                    MutableHttpResponse<?> finalResponse = alteredResponse.isPresent() ? alteredResponse.get() : response;
                    return addCsrfToken(request, finalResponse);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    protected MutableHttpResponse<?> addCsrfToken(HttpRequest<?> request, MutableHttpResponse<?> response) {
        if (csrfTokenGenerator.isEmpty() || csrfConfiguration.isEmpty()) {
            return response;
        }

        String html = response.getBody(String.class).orElse("");
        if (html.isEmpty() || (!HTML_PATTERN.matcher(html).find() && !html.startsWith("<!DOCTYPE html>"))) {
            return response;
        }

        String csrfToken = csrfTokenGenerator.get().generateCsrfToken(request);
        if (csrfToken == null) {
            return response;
        }

        String escaped = csrfToken.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
        String metaTag = "<meta name=\"csrf-token\" content=\"" + escaped + "\">";
        html = html.replaceFirst("<head>", "<head>\n" + metaTag);
        response = response.body(html);
        response.cookie(
            Cookie.of(csrfConfiguration.get().getCookieName(), csrfToken)
                .httpOnly(true)
                .secure(true)
                .sameSite(SameSite.Strict)
                .path("/")
        );
        return response;
    }

    private String replace(String line) {
        if (!line.contains("KESTRA_UI_PATH")) {
            return line;
        }

        line = line.replace("./", (basePath != null ? basePath : "") + "/ui/");

        if (webserverConfiguration.googleAnalytics() != null) {
            line = line.replace("KESTRA_GOOGLE_ANALYTICS = null;", "KESTRA_GOOGLE_ANALYTICS = '" + webserverConfiguration.googleAnalytics() + "';");
        }

        if (webserverConfiguration.htmlTitle() != null) {
            line = line.replaceFirst("<title>(.*)</title>", "<title>" + webserverConfiguration.htmlTitle() + "</title>");
        }

        line = line.replace("<meta name=\"html-head\" content=\"replace\">", webserverConfiguration.htmlHead() == null ? "" : webserverConfiguration.htmlHead());

        return line;
    }
}
