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
import java.util.List;
import java.util.regex.Pattern;

import lombok.SneakyThrows;

@Singleton
@Requires(missingClasses = "io.kestra.ee.webserver.rooting.TenantAliasingRooterEE")
@Replaces(DefaultRouter.class)
public class TenantAliasingRooter extends DefaultRouter {

    protected static final List<Pattern> EXCLUDED_ROUTES = List.of(
        Pattern.compile("/api/v1/main/.*"),
        Pattern.compile("/api/v1/configs")
    );

    @Inject
    public TenantAliasingRooter(Collection<RouteBuilder> builders) {
        super(builders);
    }

    @SneakyThrows
    @Override
    public <T, R> UriRouteMatch<T, R> findClosest(HttpRequest<?> request) {
        String path = request.getUri().getPath();
        UriRouteMatch<T, R> closest = super.findClosest(request);
        if (closest != null || bypassRooting()){
            return closest;
        }

        boolean excluded = EXCLUDED_ROUTES.stream().anyMatch(route -> route.matcher(path).matches());
        if (path.startsWith("/api/v1/") && !excluded){
            URI originalUri = request.getUri();
            URI updatedUri = new URI(
                originalUri.getScheme(),
                originalUri.getUserInfo(),
                request.getServerAddress().getHostName(),
                request.getServerAddress().getPort(),
                originalUri.getPath().replace("/api/v1", "/api/v1/" + getTenantId()),
                originalUri.getQuery(),
                originalUri.getFragment()
            );
            return super.findClosest(request.toMutableRequest().uri(updatedUri));
        }
        return null;
    }

    protected String getTenantId(){
        return "main";
    }

    /**
     * For override purpose. This method is here to allow EE version
     * to bypass rooting when some condition are met
     * @return
     */
    protected boolean bypassRooting(){
        return false;
    }
}
