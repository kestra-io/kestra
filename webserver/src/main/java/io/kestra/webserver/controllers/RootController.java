package io.kestra.webserver.controllers;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.Hidden;

/**
 * The top-level controller.
 */
@Controller
public class RootController {

    @Get("/ping")
    @Hidden
    public HttpResponse<?> ping() {
        return HttpResponse.ok("pong");
    }

}
