package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.reporter.reports.FeatureUsageReport;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void usagesShouldCoverTheRequestedNumberOfDays() {
        var oneDay = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/usages/all"), FeatureUsageReport.UsageEvent.class);
        var thirtyDays = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/usages/all?days=30"), FeatureUsageReport.UsageEvent.class);

        assertThat(thirtyDays.getExecutions().getDailyExecutionsCount()).hasSizeGreaterThan(oneDay.getExecutions().getDailyExecutionsCount().size());
        assertThat(thirtyDays.getExecutions().getDailyExecutionsCount()).hasSizeBetween(30, 31);
    }

    @Test
    void usagesShouldRejectAWindowOutsideOfTheSupportedRange() {
        assertThatThrownBy(() -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/usages/all?days=0")))
            .isInstanceOfSatisfying(HttpClientResponseException.class, e -> assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode()));
        assertThatThrownBy(() -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/usages/all?days=31")))
            .isInstanceOfSatisfying(HttpClientResponseException.class, e -> assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode()));
    }
}
