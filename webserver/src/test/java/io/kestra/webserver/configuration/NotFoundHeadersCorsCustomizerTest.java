package io.kestra.webserver.configuration;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.webserver.filter.NotFoundHeadersFilter;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.cors.CorsOriginConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotFoundHeadersCorsCustomizerTest {
    private static final NotFoundHeadersCorsCustomizer CUSTOMIZER = new NotFoundHeadersCorsCustomizer();

    @Test
    void shouldExposeBothHeadersOnEveryConfiguredCorsConfiguration() {
        // Given
        CorsOriginConfiguration ui = new CorsOriginConfiguration();
        CorsOriginConfiguration api = new CorsOriginConfiguration();
        HttpServerConfiguration.CorsConfiguration corsConfiguration = corsConfiguration(true, Map.of("ui", ui, "api", api));

        // When
        CUSTOMIZER.onCreated(event(corsConfiguration));

        // Then
        assertThat(ui.getExposedHeaders())
            .containsExactly(NotFoundHeadersFilter.EDITION_HEADER, NotFoundHeadersFilter.ROUTE_MATCHED_HEADER);
        assertThat(api.getExposedHeaders())
            .containsExactly(NotFoundHeadersFilter.EDITION_HEADER, NotFoundHeadersFilter.ROUTE_MATCHED_HEADER);
    }

    @Test
    void shouldKeepTheHeadersADeploymentAlreadyExposes() {
        // Given
        CorsOriginConfiguration originConfiguration = new CorsOriginConfiguration();
        originConfiguration.setExposedHeaders(List.of("X-Custom-Header"));
        HttpServerConfiguration.CorsConfiguration corsConfiguration =
            corsConfiguration(true, Map.of("ui", originConfiguration));

        // When
        CUSTOMIZER.onCreated(event(corsConfiguration));

        // Then
        assertThat(originConfiguration.getExposedHeaders()).containsExactly(
            "X-Custom-Header",
            NotFoundHeadersFilter.EDITION_HEADER,
            NotFoundHeadersFilter.ROUTE_MATCHED_HEADER
        );
    }

    @Test
    void shouldNotDuplicateAHeaderAlreadyExposedInADifferentCase() {
        // Given
        CorsOriginConfiguration originConfiguration = new CorsOriginConfiguration();
        originConfiguration.setExposedHeaders(List.of("x-kestra-edition"));
        HttpServerConfiguration.CorsConfiguration corsConfiguration =
            corsConfiguration(true, Map.of("ui", originConfiguration));

        // When
        CUSTOMIZER.onCreated(event(corsConfiguration));

        // Then
        assertThat(originConfiguration.getExposedHeaders())
            .containsExactly("x-kestra-edition", NotFoundHeadersFilter.ROUTE_MATCHED_HEADER);
    }

    @Test
    void shouldLeaveConfigurationsUntouchedWhenCorsIsDisabled() {
        // Given
        CorsOriginConfiguration originConfiguration = new CorsOriginConfiguration();
        HttpServerConfiguration.CorsConfiguration corsConfiguration =
            corsConfiguration(false, Map.of("ui", originConfiguration));

        // When
        CUSTOMIZER.onCreated(event(corsConfiguration));

        // Then
        assertThat(originConfiguration.getExposedHeaders()).isEmpty();
    }

    private static HttpServerConfiguration.CorsConfiguration corsConfiguration(
        boolean enabled,
        Map<String, CorsOriginConfiguration> configurations
    ) {
        HttpServerConfiguration.CorsConfiguration corsConfiguration = new HttpServerConfiguration.CorsConfiguration();
        corsConfiguration.setEnabled(enabled);
        corsConfiguration.setConfigurations(configurations);

        return corsConfiguration;
    }

    @SuppressWarnings("unchecked")
    private static BeanCreatedEvent<HttpServerConfiguration.CorsConfiguration> event(
        HttpServerConfiguration.CorsConfiguration corsConfiguration
    ) {
        BeanCreatedEvent<HttpServerConfiguration.CorsConfiguration> event = mock(BeanCreatedEvent.class);
        when(event.getBean()).thenReturn(corsConfiguration);

        return event;
    }
}
