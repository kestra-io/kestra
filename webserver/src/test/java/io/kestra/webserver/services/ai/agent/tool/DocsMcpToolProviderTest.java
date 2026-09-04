package io.kestra.webserver.services.ai.agent.tool;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.webserver.services.ai.agent.AgentConfiguration;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutor;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class DocsMcpToolProviderTest {

    @Test
    void shouldDisableAutoHealthCheckOnMcpClient(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post(urlPathMatching("/v1/mcp.*"))
            .withRequestBody(containing("notifications/"))
            .willReturn(ok()));

        stubFor(post(urlPathMatching("/v1/mcp.*"))
            .withRequestBody(containing("tools/list"))
            .willReturn(okJson("""
                {"jsonrpc":"2.0","id":2,"result":{"tools":[]}}
            """)));

        stubFor(post(urlPathMatching("/v1/mcp.*"))
            .withRequestBody(containing("initialize"))
            .willReturn(okJson("""
                {"jsonrpc":"2.0","id":0,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"mock-docs","version":"1.0"}}}
            """)));

        AgentConfiguration configuration = AgentConfiguration.builder()
            .docsMcpUrl(wmRuntimeInfo.getHttpBaseUrl() + "/v1/mcp")
            .build();

        DocsMcpToolProvider provider = new DocsMcpToolProvider(configuration);
        Map<ToolSpecification, ToolExecutor> tools = provider.tools();

        assertThat(tools).isNotNull();

        Field clientField = DocsMcpToolProvider.class.getDeclaredField("client");
        clientField.setAccessible(true);
        McpClient client = (McpClient) clientField.get(provider);

        assertThat(client).isNotNull();
        assertThat(client).isInstanceOf(DefaultMcpClient.class);

        Field autoHealthCheckField = DefaultMcpClient.class.getDeclaredField("autoHealthCheck");
        autoHealthCheckField.setAccessible(true);
        assertThat(autoHealthCheckField.get(client)).isEqualTo(Boolean.FALSE);

        Field schedulerField = DefaultMcpClient.class.getDeclaredField("healthCheckScheduler");
        schedulerField.setAccessible(true);
        assertThat(schedulerField.get(client)).isNull();
    }

    @Test
    void shouldDegradeGracefullyWhenUnreachable() {
        AgentConfiguration configuration = AgentConfiguration.builder()
            .docsMcpUrl("http://localhost:1/v1/mcp")
            .build();

        DocsMcpToolProvider provider = new DocsMcpToolProvider(configuration);
        Map<ToolSpecification, ToolExecutor> tools = provider.tools();

        assertThat(tools).isEmpty();
    }
}
