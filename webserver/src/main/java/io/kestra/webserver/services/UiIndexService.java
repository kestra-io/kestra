package io.kestra.webserver.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import io.kestra.webserver.configuration.WebserverConfiguration;
import io.kestra.webserver.utils.HttpCacheUtils;

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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Serves the UI {@code index.html}: the file is read from the classpath and rewritten (base path, analytics,
 * title, custom head) only once into an immutable template; per request only the CSRF meta tag is substituted.
 */
@Singleton
@Requires(property = "kestra.webserver.ui.enabled", notEquals = "false", defaultValue = "true")
public class UiIndexService {
    private static final String INDEX_RESOURCE = "ui/index.html";
    private static final String HEAD_TAG = "<head>";
    private static final String CACHE_CONTROL_NO_CACHE = "no-cache";
    private static final String GZIP = "gzip";

    private final String basePath;
    private final WebserverConfiguration webserverConfiguration;
    private final Optional<CsrfConfiguration> csrfConfiguration;
    private final Optional<CsrfTokenGenerator<HttpRequest<?>>> csrfTokenGenerator;

    private volatile Optional<IndexTemplate> template;

    @Inject
    public UiIndexService(
        @Nullable @Value("${micronaut.server.context-path}") String basePath,
        WebserverConfiguration webserverConfiguration,
        Optional<CsrfConfiguration> csrfConfiguration,
        Optional<CsrfTokenGenerator<HttpRequest<?>>> csrfTokenGenerator
    ) {
        this.basePath = basePath;
        this.webserverConfiguration = Objects.requireNonNull(webserverConfiguration);
        this.csrfConfiguration = Objects.requireNonNull(csrfConfiguration);
        this.csrfTokenGenerator = Objects.requireNonNull(csrfTokenGenerator);
    }

    /**
     * Renders the full {@code index.html} response for the given request, or empty when the UI is not
     * packaged on the classpath.
     */
    public Optional<MutableHttpResponse<byte[]>> render(HttpRequest<?> request) {
        return template().map(indexTemplate -> render(request, indexTemplate));
    }

    private MutableHttpResponse<byte[]> render(HttpRequest<?> request, IndexTemplate indexTemplate) {
        String html = indexTemplate.html();
        Cookie csrfCookie = null;

        if (csrfConfiguration.isPresent() && csrfTokenGenerator.isPresent()) {
            // Reuse the existing cookie token so multiple tabs and BFCache-restored pages
            // all share one stable token. Generate only when the cookie is absent.
            String csrfToken = request.getCookies()
                .findCookie(csrfConfiguration.get().getCookieName())
                .map(Cookie::getValue)
                .orElseGet(() -> csrfTokenGenerator.get().generateCsrfToken(request));

            if (csrfToken != null) {
                String escaped = csrfToken.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
                html = indexTemplate.withCsrfMeta("<meta name=\"csrf-token\" content=\"" + escaped + "\">");
                csrfCookie = Cookie.of(csrfConfiguration.get().getCookieName(), csrfToken)
                    .httpOnly(true)
                    .secure(request.isSecure())
                    .sameSite(SameSite.Strict)
                    .path("/");
            }
        }

        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        String acceptEncoding = request.getHeaders().get(HttpHeaders.ACCEPT_ENCODING);
        String contentEncoding = HttpCacheUtils.accepts(acceptEncoding, GZIP) ? GZIP : null;
        String etag = HttpCacheUtils.etagFor(HttpCacheUtils.sha256Hex(body), contentEncoding);

        if (HttpCacheUtils.anyEtagMatches(request.getHeaders().get(HttpHeaders.IF_NONE_MATCH), etag)) {
            return applyHeaders(HttpResponse.notModified(), etag);
        }

        if (GZIP.equals(contentEncoding)) {
            body = gzip(body);
        }

        MutableHttpResponse<byte[]> response = applyHeaders(HttpResponse.ok(body), etag)
            .contentType(MediaType.TEXT_HTML_TYPE)
            .contentLength(body.length);
        if (contentEncoding != null) {
            response.header(HttpHeaders.CONTENT_ENCODING, contentEncoding);
        }
        if (csrfCookie != null) {
            response.cookie(csrfCookie);
        }
        return response;
    }

    private static <T> MutableHttpResponse<T> applyHeaders(MutableHttpResponse<T> response, String etag) {
        return response
            .header(HttpHeaders.ETAG, etag)
            .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_CACHE)
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
    }

    private Optional<IndexTemplate> template() {
        Optional<IndexTemplate> local = this.template;
        if (local == null) {
            synchronized (this) {
                if (this.template == null) {
                    this.template = load();
                }
                local = this.template;
            }
        }
        return local;
    }

    private Optional<IndexTemplate> load() {
        try (InputStream is = UiIndexService.class.getClassLoader().getResourceAsStream(INDEX_RESOURCE)) {
            if (is == null) {
                return Optional.empty();
            }
            String html = replace(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            int headIndex = html.indexOf(HEAD_TAG);
            if (headIndex < 0) {
                return Optional.of(new IndexTemplate(html, null, null));
            }
            int insertionPoint = headIndex + HEAD_TAG.length();
            return Optional.of(new IndexTemplate(html, html.substring(0, insertionPoint), html.substring(insertionPoint)));
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

    // index.html is small and rewritten per user (CSRF token), so this is the one place content
    // is still compressed on the request path.
    private static byte[] gzip(byte[] raw) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(Math.max(64, raw.length / 3));
        try (java.util.zip.GZIPOutputStream stream = new java.util.zip.GZIPOutputStream(out)) {
            stream.write(raw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * The immutable, startup-rewritten index page, pre-split at the {@code <head>} insertion point so the
     * per-request CSRF meta substitution is a plain concatenation.
     */
    private record IndexTemplate(String html, @Nullable String headPrefix, @Nullable String headSuffix) {
        String withCsrfMeta(String metaTag) {
            if (headPrefix == null) {
                return html;
            }
            return headPrefix + "\n" + metaTag + headSuffix;
        }
    }
}
