package io.kestra.webserver.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.util.Base64;
import java.util.List;

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

    @Value("${kestra.server.open-urls:[]}")
    private List<String> openUrls;

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        boolean isOpenUrl = this.openUrls
            .stream()
            .anyMatch(s -> request.getPath().startsWith(s));

        if (isOpenUrl) {
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

    record BasicAuth(String username, String password) {
        static BasicAuth from(String authentication) {
            var decoded = new String(Base64.getDecoder().decode(authentication));
            var username = decoded.substring(0, decoded.indexOf(':'));
            var password = decoded.substring(decoded.indexOf(':') + 1);
            return new BasicAuth(username, password);
        }
    }
}
