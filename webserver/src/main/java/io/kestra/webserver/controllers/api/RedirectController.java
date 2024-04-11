package io.kestra.webserver.controllers.api;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Slf4j
@Controller
public class RedirectController {
    @Nullable
    @Value("${micronaut.server.context-path}")
    protected String basePath;

    @Get
    @Hidden
    public HttpResponse<?> slash() {
        return HttpResponse.temporaryRedirect(URI.create((basePath != null ? basePath : "") + "/ui/"));
    }
}
