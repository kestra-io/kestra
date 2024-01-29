package io.kestra.webserver.filter;

import io.kestra.core.utils.AuthUtils;
import io.kestra.webserver.services.BasicAuthService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.RouteMatchUtils;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Base64;
import java.util.Optional;

@Filter("/**")
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
public class AuthenticationFilter implements HttpServerFilter {
    private static final String PREFIX = "Basic";

    @Inject
    private BasicAuthService basicAuthService;

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        if (!basicAuthService.isEnabled()) {
            return chain.proceed(request);
        }

        BasicAuthService.SaltedBasicAuthConfiguration basicAuthConfiguration = this.basicAuthService.configuration();
        boolean isOpenUrl = basicAuthConfiguration.getOpenUrls()
            .stream()
            .anyMatch(s -> request.getPath().startsWith(s));

        if (isOpenUrl || isManagementEndpoint(request)) {
            return Flux.from(chain.proceed(request));
        }

        var basicAuth = request
            .getHeaders()
            .getAuthorization()
            .filter(auth -> auth.toLowerCase().startsWith(PREFIX.toLowerCase()))
            .map(cred -> BasicAuth.from(cred.substring(PREFIX.length() + 1)));

        if (basicAuth.isEmpty() ||
            !basicAuth.get().username().equals(basicAuthConfiguration.getUsername()) ||
            !AuthUtils.encodePassword(basicAuthConfiguration.getSalt(), basicAuth.get().password()).equals(basicAuthConfiguration.getPassword())
        ) {
            return Flux.just(HttpResponse.unauthorized().header("WWW-Authenticate", PREFIX + " realm=" + basicAuthConfiguration.getRealm()));
        }

        return Flux.from(chain.proceed(request));
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
