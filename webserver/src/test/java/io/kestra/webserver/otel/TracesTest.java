package io.kestra.webserver.otel;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlowsWithTenant;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.trace.TraceUtils;
import io.kestra.webserver.tenants.TenantValidationFilter;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.annotation.MockBean;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest(startRunner = true, environments = {"test", "otel"})
public class TracesTest {
    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @MockBean(TenantService.class)
    public TenantService getTenantService() {
        return mock(TenantService.class);
    }

    @Inject
    private TenantService tenantService;

    @MockBean(TenantValidationFilter.class)
    public TenantValidationFilter getTenantValidationFilter() {
        return mock(TenantValidationFilter.class);
    }

    @Test
    @LoadFlowsWithTenant({"flows/valids/minimal.yaml"})
    void runningAFlowShouldGenerateTraces(String tenantId) {
        when(tenantService.resolveTenant()).thenReturn(tenantId);

        // running a flow until completion
        Execution result = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/%s/executions/io.kestra.tests/minimal?wait=true".formatted(tenantId), null)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );
        assertThat(result.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        List<SpanData> spans = otelTesting.getSpans().stream().filter(span -> tenantId.equals(span.getAttributes().get(TraceUtils.ATTR_TENANT_ID))).toList();
        assertThat(spans).hasSizeGreaterThanOrEqualTo(6); // rarely, CI shows 6 traces and not 7 and even sometimes 14, probably due to asynchronicity
        assertThat(spans).extracting(SpanData::getName).contains("EXECUTOR - %s_io.kestra.tests_minimal".formatted(tenantId), "WORKER - io.kestra.plugin.core.debug.Return");
        Attributes attributes = spans.getFirst().getAttributes();
        assertThat(attributes.size()).isEqualTo(5);
        assertThat(attributes.get(TraceUtils.ATTR_TENANT_ID)).isEqualTo(tenantId);
        assertThat(attributes.get(TraceUtils.ATTR_NAMESPACE)).isEqualTo("io.kestra.tests");
        assertThat(attributes.get(TraceUtils.ATTR_FLOW_ID)).isEqualTo("minimal");
        assertThat(attributes.get(TraceUtils.ATTR_EXECUTION_ID)).isEqualTo(result.getId());
        assertThat(attributes.get(TraceUtils.ATTR_SOURCE)).isEqualTo("io.kestra.jdbc.runner.JdbcExecutor");
    }

    @MockBean
    public OpenTelemetry openTelemetry() {
        return otelTesting.getOpenTelemetry();
    }

    @MockBean
    public Tracer tracer() {
        return otelTesting.getOpenTelemetry().getTracer("kestra");
    }
}
