package io.kestra.webserver.listeners;

import io.kestra.webserver.models.events.OssAuthEvent;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.retry.event.RetryEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class OssAuthListener {
    @Inject
    @Client("api")
    private HttpClient httpClient;

    @EventListener
    void onOssAuth(final OssAuthEvent event) {
        httpClient.toBlocking().exchange(HttpRequest.POST(
            "/v1/reports/events",
            event
        ));
    }
}

