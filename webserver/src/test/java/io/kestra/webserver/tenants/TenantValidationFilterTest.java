package io.kestra.webserver.tenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.flows.Flow;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@KestraTest
public class TenantValidationFilterTest {

    private static final String NAMESPACE = "io.kestra.tests";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void should_find_flow_for_no_tenant() {
        Flow flow = client.toBlocking()
            .retrieve(
                HttpRequest.GET("/api/v1/flows/" + NAMESPACE + "/inputs"),
                Flow.class
            );
        assertThat(flow).isNotNull();
        assertThat(flow.getId()).isEqualTo("inputs");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void should_find_flow_for_main_tenant() {
        Flow flow = client.toBlocking()
            .retrieve(
                HttpRequest.GET("/api/v1/main/flows/" + NAMESPACE + "/inputs"),
                Flow.class
            );
        assertThat(flow).isNotNull();
        assertThat(flow.getId()).isEqualTo("inputs");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void should_return_bad_request_for_flow_with_incorrect_tenant() {
        HttpClientResponseException excetpion = catchThrowableOfType(
            HttpClientResponseException.class,
            () -> client.toBlocking()
                .retrieve(
                    HttpRequest.GET("/api/v1/non_main_tenant/flows/" + NAMESPACE + "/inputs"),
                    Flow.class
                ));
        assertThat(excetpion.code()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(excetpion.getMessage()).isEqualTo("Bad Request: Tenant must be 'main' for OSS version");
    }
}
