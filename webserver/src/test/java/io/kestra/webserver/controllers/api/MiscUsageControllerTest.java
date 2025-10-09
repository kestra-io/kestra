package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.reporter.reports.FeatureUsageReport;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@KestraTest
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