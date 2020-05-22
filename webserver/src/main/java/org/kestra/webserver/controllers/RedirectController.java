package org.kestra.webserver.controllers;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Slf4j
@Controller
public class RedirectController {
    @Get
    public HttpResponse<?> slash() {
        return HttpResponse.temporaryRedirect(URI.create("/ui/"));
    }
}
