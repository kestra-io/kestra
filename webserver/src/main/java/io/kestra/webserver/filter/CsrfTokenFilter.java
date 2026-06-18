package io.kestra.webserver.filter;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.security.csrf.resolver.CsrfTokenResolver;
import io.micronaut.security.csrf.validator.CsrfTokenValidator;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_COOKIE_NAME;

/**
 * Custom CSRF filter that only enforces token validation on cookie-authenticated requests.
 * SDK/API clients using the Authorization header bypass CSRF since they are not vulnerable
 * to cross-site request forgery (attackers cannot set custom headers cross-origin).
 */
@Requires(property = "micronaut.security.csrf.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Requires(beans = CsrfTokenValidator.class)
@ServerFilter(patternStyle = FilterPatternStyle.REGEX, value = "/api/.*")
@Slf4j
public class CsrfTokenFilter implements Ordered {
    private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.TRACE);

    private final List<CsrfTokenResolver<HttpRequest<?>>> csrfTokenResolvers;
    private final CsrfTokenValidator<HttpRequest<?>> csrfTokenValidator;
    private final HttpServerConfiguration serverConfiguration;

    public CsrfTokenFilter(List<CsrfTokenResolver<HttpRequest<?>>> csrfTokenResolvers,
                            CsrfTokenValidator<HttpRequest<?>> csrfTokenValidator,
                            HttpServerConfiguration serverConfiguration) {
        this.csrfTokenResolvers = csrfTokenResolvers;
        this.csrfTokenValidator = csrfTokenValidator;
        this.serverConfiguration = serverConfiguration;
    }

    @RequestFilter
    @Nullable
    public HttpResponse<?> csrfFilter(@NonNull HttpRequest<?> request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return null;
        }

        if (!hasCookieAuth(request)) {
            return null;
        }

        if (isCorsOrigin(request)) {
            return null;
        }

        String csrfToken = resolveCsrfToken(request);
        if (StringUtils.isEmpty(csrfToken)) {
            log.debug("CSRF rejected for {} {}: cookie-authenticated request with no CSRF token (missing X-CSRF-TOKEN header/field)",
                request.getMethod(), request.getPath());
            return HttpResponse.status(HttpStatus.FORBIDDEN);
        }
        if (!csrfTokenValidator.validateCsrfToken(request, csrfToken)) {
            log.debug("CSRF rejected for {} {}: token present but failed validation (bad signature or mismatched token)",
                request.getMethod(), request.getPath());
            return HttpResponse.status(HttpStatus.FORBIDDEN);
        }

        return null;
    }

    private static final String JWT_COOKIE_NAME = "JWT";

    private boolean hasCookieAuth(@NonNull HttpRequest<?> request) {
        return request.getCookies().findCookie(BASIC_AUTH_COOKIE_NAME).isPresent()
            || request.getCookies().findCookie(JWT_COOKIE_NAME).isPresent();
    }

    @Nullable
    private String resolveCsrfToken(@NonNull HttpRequest<?> request) {
        for (CsrfTokenResolver<HttpRequest<?>> resolver : csrfTokenResolvers) {
            Optional<String> token = resolver.resolveToken(request);
            if (token.isPresent()) {
                return token.get();
            }
        }
        return null;
    }

    private boolean isCorsOrigin(@NonNull HttpRequest<?> request) {
        String origin = request.getHeaders().get(HttpHeaders.ORIGIN);
        if (origin == null) {
            return false;
        }
        var corsConfig = serverConfiguration.getCors();
        if (!corsConfig.isEnabled()) {
            return false;
        }
        return corsConfig.getConfigurations().values().stream()
            .flatMap(c -> c.getAllowedOrigins().stream())
            .anyMatch(origin::equals);
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order() + 100;
    }
}
