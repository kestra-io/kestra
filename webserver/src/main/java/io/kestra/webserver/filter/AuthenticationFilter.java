package io.kestra.webserver.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
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
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Filter("/**")
@Requires(property = "kestra.server.basic-auth.enabled", value = "true")
public class AuthenticationFilter implements HttpServerFilter {
    private static final String PREFIX = "Basic";

    @Value("${kestra.server.basic-auth.username}")
    private String username;

    @Value("${kestra.server.basic-auth.password}")
    private String password;

    @Value("${kestra.server.basic-auth.realm:Kestra}")
    private String realm;

    @Value("${kestra.server.basic-auth.open-urls:[]}")
    private List<String> openUrls;

    @Value("${endpoints.all.port:8085}")
    private int managementPort;

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        boolean isOpenUrl = this.openUrls
            .stream()
            .anyMatch(s -> request.getPath().startsWith(s));

        if (isOpenUrl || isManagementEndpoint(request)) {
            return Flowable.fromPublisher(chain.proceed(request));
        }

        var basicAuth = request
            .getHeaders()
            .getAuthorization()
            .filter(auth -> auth.toLowerCase().startsWith(PREFIX.toLowerCase()))
            .map(cred -> BasicAuth.from(cred.substring(PREFIX.length() + 1)));

        if (basicAuth.isEmpty() ||
            !basicAuth.get().username().equals(username) ||
            !basicAuth.get().password().equals(password)
        ) {
            return Flowable.just(HttpResponse.unauthorized().header("WWW-Authenticate", PREFIX + " realm=" + realm));
        }

        return Flowable.fromPublisher(chain.proceed(request));
    }

    @SuppressWarnings("rawtypes")
    private boolean isManagementEndpoint(HttpRequest<?> request) {
        Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatch(request);
        if (routeMatch.isPresent() && routeMatch.get() instanceof MethodBasedRouteMatch<?,?> method) {
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
