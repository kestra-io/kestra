package io.kestra.webserver.controllers.api;

import io.kestra.core.plugins.endpoint.PluginEndpointExecutionException;
import io.kestra.core.plugins.endpoint.PluginEndpointResponse;
import io.kestra.core.plugins.endpoint.PluginEndpointService;
import io.kestra.core.tenant.TenantService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginEndpointControllerTest {
    private static final String GROUP = "io.kestra.plugin.ai";

    private static TenantService tenantService() {
        TenantService tenantService = mock(TenantService.class);
        when(tenantService.resolveTenant()).thenReturn("main");
        return tenantService;
    }

    @SuppressWarnings("unchecked")
    private static PluginEndpointService serviceReturning(PluginEndpointResponse response) {
        PluginEndpointService service = mock(PluginEndpointService.class);
        when(service.dispatch(any(), any(), any(), any(), any(), any(), any())).thenReturn(response);
        return service;
    }

    private static PluginEndpointController controllerReturning(PluginEndpointResponse response) {
        return new PluginEndpointController(serviceReturning(response), tenantService());
    }

    @Test
    void shouldServeJsonInlineWithNosniff() {
        PluginEndpointController controller = controllerReturning(PluginEndpointResponse.of(Map.of("message", "hi")));

        HttpResponse<byte[]> response = controller.get(HttpRequest.GET("/"), GROUP, "hello", "exec1", "tr1");

        assertThat(response.status().getCode()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("hi");
        assertThat(response.getHeaders().get("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().contains(HttpHeaders.CONTENT_DISPOSITION)).isFalse();
    }

    @Test
    void shouldForceDownloadWithFileNameForFileResponse() {
        PluginEndpointController controller = controllerReturning(
            PluginEndpointResponse.ofFile("x".getBytes(StandardCharsets.UTF_8), "application/octet-stream", "report.parquet"));

        HttpResponse<byte[]> response = controller.get(HttpRequest.GET("/"), GROUP, "hello", "exec1", "tr1");

        assertThat(response.getHeaders().get("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment; filename=\"report.parquet\"");
    }

    @Test
    void shouldSanitizePluginControlledFileName() {
        PluginEndpointController controller = controllerReturning(
            PluginEndpointResponse.ofFile("x".getBytes(StandardCharsets.UTF_8), "application/octet-stream", "a\"b\r\n\tc/d\\e.txt"));

        HttpResponse<byte[]> response = controller.get(HttpRequest.GET("/"), GROUP, "hello", "exec1", "tr1");

        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment; filename=\"a_b___c_d_e.txt\"");
    }

    @Test
    void shouldForceDownloadWithoutFileNameWhenNoneGiven() {
        PluginEndpointController controller = controllerReturning(
            PluginEndpointResponse.ofBytes("x".getBytes(StandardCharsets.UTF_8), "application/octet-stream"));

        HttpResponse<byte[]> response = controller.get(HttpRequest.GET("/"), GROUP, "hello", "exec1", "tr1");

        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment");
    }

    @Test
    void shouldRejectEmptyPostBodyAsUnprocessable() {
        PluginEndpointController controller = controllerReturning(PluginEndpointResponse.of(Map.of()));

        assertThatThrownBy(() -> controller.post(HttpRequest.POST("/", ""), GROUP, "hello", "exec1", "tr1", new byte[0]))
            .isInstanceOfSatisfying(HttpStatusException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode()));
    }

    @Test
    void shouldRejectNonObjectPostBodyAsBadRequest() {
        PluginEndpointController controller = controllerReturning(PluginEndpointResponse.of(Map.of()));

        assertThatThrownBy(() -> controller.post(HttpRequest.POST("/", ""), GROUP, "hello", "exec1", "tr1",
            "[1,2]".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOfSatisfying(HttpStatusException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode()));
    }

    @Test
    void shouldRejectNullJsonBodyAsBadRequest() {
        PluginEndpointController controller = controllerReturning(PluginEndpointResponse.of(Map.of()));

        assertThatThrownBy(() -> controller.post(HttpRequest.POST("/", ""), GROUP, "hello", "exec1", "tr1",
            "null".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOfSatisfying(HttpStatusException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldParseJsonObjectPostBodyIntoMap() {
        PluginEndpointService service = serviceReturning(PluginEndpointResponse.of(Map.of("ok", true)));
        PluginEndpointController controller = new PluginEndpointController(service, tenantService());

        controller.post(HttpRequest.POST("/", ""), GROUP, "hello", "exec1", "tr1",
            "{\"k\":\"v\"}".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(service).dispatch(eq("main"), eq(GROUP), eq("hello"), eq("exec1"), eq("tr1"), any(), body.capture());
        assertThat(body.getValue()).containsExactlyEntriesOf(Map.of("k", "v"));
    }

    @Test
    void shouldReturnHardenedServerErrorWithoutLeakingDetailWhenHandlerFails() {
        PluginEndpointService service = mock(PluginEndpointService.class);
        when(service.dispatch(eq("main"), eq(GROUP), eq("boom"), any(), any(), any(), any()))
            .thenThrow(new PluginEndpointExecutionException(GROUP, "boom",
                new RuntimeException("secret-detail-should-not-leak")));
        PluginEndpointController controller = new PluginEndpointController(service, tenantService());

        HttpResponse<byte[]> response = controller.get(HttpRequest.GET("/"), GROUP, "boom", "exec1", "tr1");

        assertThat(response.status().getCode()).isGreaterThanOrEqualTo(500);
        String body = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("secret-detail-should-not-leak");
        assertThat(body).contains("failed to process the request");
    }
}
