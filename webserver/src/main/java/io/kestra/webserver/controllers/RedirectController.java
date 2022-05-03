package io.kestra.webserver.controllers;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Slf4j
@Controller
public class RedirectController {
    @Get
    @Hidden
    public HttpResponse<?> slash() {
        return HttpResponse.temporaryRedirect(URI.create("/ui/"));
    }
}
