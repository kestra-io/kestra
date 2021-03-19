package io.kestra.webserver.controllers;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import io.kestra.core.utils.VersionProvider;

import javax.inject.Inject;

@Slf4j
@Controller
public class MiscController {
    @Inject
    VersionProvider versionProvider;

    @Get("/ping")
    public HttpResponse<?> ping() {
        return HttpResponse.ok("pong");
    }


    @Get("/api/v1/version")
    @ExecuteOn(TaskExecutors.IO)
    public Version version() {
        return new Version(versionProvider.getVersion());
    }

    @Value
    public static class Version {
        String version;
    }
}
