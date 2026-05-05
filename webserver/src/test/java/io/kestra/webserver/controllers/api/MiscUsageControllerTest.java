package io.kestra.webserver.controllers.api;

import io.kestra.core.models.ServerType;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.reporter.reports.FeatureUsageReport;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@KestraTest()
@Property(name = "kestra.server-type", value = "WEBSERVER")
class MiscUsageControllerTest {

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void usages() {
        var response = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/usages/all"), FeatureUsageReport.UsageEvent.class);
        assertThat(response).isNotNull();
    }
}