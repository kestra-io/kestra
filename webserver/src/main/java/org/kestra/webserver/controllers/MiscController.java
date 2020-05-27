package org.kestra.webserver.controllers;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class MiscController {
    @Get("/ping")
    public HttpResponse<?> ping() {
        return HttpResponse.ok("pong");
    }
}
