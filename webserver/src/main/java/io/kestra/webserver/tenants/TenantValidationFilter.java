package io.kestra.webserver.tenants;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.uri.UriMatchInfo;

@ServerFilter("/**")
public class TenantValidationFilter implements Ordered {
    public static final String TENANT_PATH_ATTRIBUTES = "tenant";

    @RequestFilter
    public void filterRequest(HttpRequest<?> request) {
        UriMatchInfo routeMatch = BasicHttpAttributes.getRouteMatchInfo(request).orElse(null);
        if (routeMatch != null && routeMatch.getVariableValues().containsKey(TENANT_PATH_ATTRIBUTES)) {
            String tenant = (String) routeMatch.getVariableValues().get(TENANT_PATH_ATTRIBUTES);
            if (tenant != null && !MAIN_TENANT.equals(tenant)) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Tenant must be 'main' for OSS version");
            }
        }
    }
}