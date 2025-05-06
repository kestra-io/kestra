package io.kestra.webserver.rooting;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.RouteBuilder;
import io.micronaut.web.router.UriRouteMatch;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.util.Collection;
import lombok.SneakyThrows;

@Singleton
@Requires(missingClasses = "io.kestra.ee.webserver.rooting.DefaultTenantAliasingRooter")
@Replaces(DefaultRouter.class)
public class TenantAliasingRooter extends DefaultRouter {

    @Inject
    public TenantAliasingRooter(Collection<RouteBuilder> builders) {
        super(builders);
    }

    @SneakyThrows
    @Override
    public <T, R> UriRouteMatch<T, R> findClosest(HttpRequest<?> request) {
        String path = request.getUri().getPath();
        if (!path.matches("/api/v1/main/.*")){
            return super.findClosest(request.toMutableRequest()
                .uri(new URI(request.getUri().toString().replace("/v1", "/v1/main"))));
        }
        return super.findClosest(request);
    }
}
