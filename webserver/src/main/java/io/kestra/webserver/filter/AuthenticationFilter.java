package io.kestra.webserver.filter;

import io.kestra.core.utils.AuthUtils;
import io.kestra.webserver.services.BasicAuthService;
import io.kestra.webserver.services.BasicAuthService.SaltedBasicAuthConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.RouteMatchUtils;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;

import java.util.Base64;
import java.util.Collection;
import java.util.Optional;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

//We want to authenticate only Kestra endpoints
@Filter("/api/v1/**")
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "micronaut.security.enabled", notEquals = "true") // don't add this filter in EE
public class AuthenticationFilter implements HttpServerFilter {
    private static final String PREFIX = "Basic";
    private static final Integer ORDER = ServerFilterPhase.SECURITY.order();
    public static final String BASIC_AUTH_COOKIE_NAME = "BASIC_AUTH";

    @Inject
    private BasicAuthService basicAuthService;


    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return Mono.fromCallable(() -> {
                SaltedBasicAuthConfiguration configuration = basicAuthService.configuration();
                if (configuration == null ){
                    configuration = new SaltedBasicAuthConfiguration();
                }
                return configuration;
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flux()
            .flatMap(basicAuthConfiguration -> {
                boolean isConfigEndpoint = request.getPath().endsWith("/configs") || request.getPath().endsWith("/basicAuth");

                boolean isOpenUrl = Optional.ofNullable(basicAuthConfiguration.getOpenUrls())
                    .map(Collection::stream)
                    .map(stream -> stream.anyMatch(s -> request.getPath().startsWith(s)))
                    .orElse(false);

                if (isConfigEndpoint || isOpenUrl || isManagementEndpoint(request)) {
                    return chain.proceed(request);
                }

                var basicAuth = fromCookie(request)
                    .or(() -> fromAuthorizationHeader(request))
                    .map(BasicAuth::from);

                if (basicAuth.isEmpty() ||
                    !basicAuth.get().username().equals(basicAuthConfiguration.getUsername()) ||
                    !AuthUtils.encodePassword(basicAuthConfiguration.getSalt(),
                        basicAuth.get().password()).equals(basicAuthConfiguration.getPassword())
                ) {
                    Boolean isFromLoginPage = Optional.ofNullable(request.getHeaders().get("Referer")).map(referer -> referer.split("\\?")[0].endsWith("/login")).orElse(false);

                    return Mono.just(HttpResponse.unauthorized())
                        .map(response -> isFromLoginPage ? response : response.header("WWW-Authenticate", "Basic"));
                }

                return chain.proceed(request);
            });
    }

    private Optional<String> fromCookie(HttpRequest<?> request) {
        try {
            return Optional.ofNullable(
                request.getCookies()
                    .get(BASIC_AUTH_COOKIE_NAME)
            ).map(Cookie::getValue);
        } catch (Exception e) {
            // Can happen in tests because getCookies() is not implemented in NettyClientHttpRequest but is in NettyHttpRequest
            return Optional.empty();
        }
    }

    private Optional<String> fromAuthorizationHeader(HttpRequest<?> request) {
        return request.getHeaders()
            .getAuthorization()
            .filter(auth -> auth.toLowerCase().startsWith(PREFIX.toLowerCase()))
            .map(cred -> cred.substring(PREFIX.length() + 1));
    }

    @SuppressWarnings("rawtypes")
    private boolean isManagementEndpoint(HttpRequest<?> request) {
        Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatch(request);
        if (routeMatch.isPresent() && routeMatch.get() instanceof MethodBasedRouteMatch<?, ?> method) {
            return method.getAnnotation(Endpoint.class) != null;
        }
        return false;
    }

    record BasicAuth(String username, String password) {
        static BasicAuth from(String authentication) {
            var decoded = new String(Base64.getDecoder().decode(authentication));
            var username = decoded.substring(0, decoded.indexOf(':'));
            var password = decoded.substring(decoded.indexOf(':') + 1);
            return new BasicAuth(username, password);
        }
    }
}
