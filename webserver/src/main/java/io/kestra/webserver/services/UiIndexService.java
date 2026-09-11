package io.kestra.webserver.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import io.kestra.webserver.configuration.WebserverConfiguration;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.SameSite;
import io.micronaut.security.csrf.CsrfConfiguration;
import io.micronaut.security.csrf.generator.CsrfTokenGenerator;
import io.micronaut.security.csrf.validator.CsrfTokenValidator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Serves the UI {@code index.html}: the file is read from the classpath and rewritten (base path, analytics,
 * title, custom head) only once into an immutable template; per request only the CSRF meta tag is inserted.
 * <p>
 * The response carries the user's CSRF token, so it is never cacheable and gets no entity tag.
 */
@Singleton
@Requires(property = "kestra.webserver.ui.enabled", notEquals = "false", defaultValue = "true")
public class UiIndexService {
    private static final String INDEX_RESOURCE = "ui/index.html";
    private static final String HEAD_TAG = "<head>";
    // 'private' keeps a shared cache from ever storing another user's token; 'no-store' would also
    // disqualify the page from the browser back/forward cache.
    private static final String CACHE_CONTROL = "no-cache, private";

    private final String basePath;
    private final WebserverConfiguration webserverConfiguration;
    private final Optional<CsrfConfiguration> csrfConfiguration;
    private final Optional<CsrfTokenGenerator<HttpRequest<?>>> csrfTokenGenerator;
    private final Optional<CsrfTokenValidator<HttpRequest<?>>> csrfTokenValidator;

    // Empty when the UI is not packaged on the classpath (backend-only builds).
    private final Optional<String> template;

    @Inject
    public UiIndexService(
        @Nullable @Value("${micronaut.server.context-path}") String basePath,
        WebserverConfiguration webserverConfiguration,
        Optional<CsrfConfiguration> csrfConfiguration,
        Optional<CsrfTokenGenerator<HttpRequest<?>>> csrfTokenGenerator,
        Optional<CsrfTokenValidator<HttpRequest<?>>> csrfTokenValidator
    ) {
        this.basePath = basePath;
        this.webserverConfiguration = Objects.requireNonNull(webserverConfiguration);
        this.csrfConfiguration = Objects.requireNonNull(csrfConfiguration);
        this.csrfTokenGenerator = Objects.requireNonNull(csrfTokenGenerator);
        this.csrfTokenValidator = Objects.requireNonNull(csrfTokenValidator);
        this.template = load();
    }

    /**
     * Renders the full {@code index.html} response for the given request, or empty when the UI is not
     * packaged on the classpath.
     */
    public Optional<MutableHttpResponse<byte[]>> render(HttpRequest<?> request) {
        return template.map(html -> render(request, html));
    }

    private MutableHttpResponse<byte[]> render(HttpRequest<?> request, String template) {
        String html = template;
        Cookie csrfCookie = null;

        if (csrfConfiguration.isPresent() && csrfTokenGenerator.isPresent()) {
            // Reuse the existing cookie token so multiple tabs and BFCache-restored pages
            // all share one stable token. Generate only when the cookie is absent or holds a
            // token this instance cannot validate: a cookie left behind by another Kestra
            // instance on the same host (an OSS deployment replaced by EE, a rotated
            // kestra.encryption.secret-key) is signed with a key this instance rejects, and
            // echoing it into the page would make every cookie-authenticated write fail CSRF
            // validation until the browser's cookies are cleared.
            String csrfToken = request.getCookies()
                .findCookie(csrfConfiguration.get().getCookieName())
                .map(Cookie::getValue)
                .filter(token -> isValidCsrfToken(request, token))
                .orElseGet(() -> csrfTokenGenerator.get().generateCsrfToken(request));

            if (csrfToken != null) {
                String escaped = csrfToken.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
                html = withCsrfMeta(template, "<meta name=\"csrf-token\" content=\"" + escaped + "\">");
                csrfCookie = Cookie.of(csrfConfiguration.get().getCookieName(), csrfToken)
                    .httpOnly(true)
                    .secure(request.isSecure())
                    .sameSite(SameSite.Strict)
                    .path("/");
            }
        }

        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        MutableHttpResponse<byte[]> response = HttpResponse.ok(body)
            .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
            .contentType(MediaType.TEXT_HTML_TYPE)
            .contentLength(body.length);
        if (csrfCookie != null) {
            response.cookie(csrfCookie);
        }
        return response;
    }

    // Without a validator bean every cookie token is trusted, as it was before the check existed.
    private boolean isValidCsrfToken(HttpRequest<?> request, String token) {
        return csrfTokenValidator
            .map(validator -> validator.validateCsrfToken(request, token))
            .orElse(true);
    }

    // Plain concatenation rather than replaceFirst: a token is untrusted input for a regex replacement.
    private static String withCsrfMeta(String html, String metaTag) {
        int headIndex = html.indexOf(HEAD_TAG);
        if (headIndex < 0) {
            return html;
        }
        int insertionPoint = headIndex + HEAD_TAG.length();
        return html.substring(0, insertionPoint) + "\n" + metaTag + html.substring(insertionPoint);
    }

    private Optional<String> load() {
        try (InputStream is = UiIndexService.class.getClassLoader().getResourceAsStream(INDEX_RESOURCE)) {
            if (is == null) {
                return Optional.empty();
            }
            return Optional.of(replace(new String(is.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
