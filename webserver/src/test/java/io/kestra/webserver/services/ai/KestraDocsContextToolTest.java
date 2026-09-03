package io.kestra.webserver.services.ai;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import io.kestra.core.utils.VersionProvider;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockTest
class KestraDocsContextToolTest {

    @Test
    void shouldDisableAutoHealthCheckOnMcpClient(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post(urlPathMatching("/v1/mcp.*"))
            .willReturn(okJson("""
                {"jsonrpc":"2.0","id":0,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"mock-docs","version":"1.0"}}}
            """)));

        VersionProvider versionProvider = mock(VersionProvider.class);
        when(versionProvider.getVersion()).thenReturn("2.1.0");

        try (KestraDocsContextTool tool = new KestraDocsContextTool(wmRuntimeInfo.getHttpBaseUrl(), versionProvider)) {
            Field clientField = KestraDocsContextTool.class.getDeclaredField("mcpClient");
            clientField.setAccessible(true);
            McpClient client = (McpClient) clientField.get(tool);

            assertThat(client).isNotNull();
            assertThat(client).isInstanceOf(DefaultMcpClient.class);

            Field autoHealthCheckField = DefaultMcpClient.class.getDeclaredField("autoHealthCheck");
            autoHealthCheckField.setAccessible(true);
            assertThat(autoHealthCheckField.get(client)).isEqualTo(Boolean.FALSE);

            Field schedulerField = DefaultMcpClient.class.getDeclaredField("healthCheckScheduler");
            schedulerField.setAccessible(true);
            assertThat(schedulerField.get(client)).isNull();
        }
    }

    @Test
    void shouldDegradeGracefullyWhenUnreachable() {
        VersionProvider versionProvider = mock(VersionProvider.class);
        when(versionProvider.getVersion()).thenReturn("2.1.0");

        try (KestraDocsContextTool tool = new KestraDocsContextTool("http://localhost:1", versionProvider)) {
            assertThat(tool.searchDocs("flow")).isNull();
            assertThat(tool.getDoc("url")).isNull();
            assertThat(tool.searchBlueprints("query")).isNull();
            assertThat(tool.getBlueprintFlow(123)).isNull();
        }
    }
}
