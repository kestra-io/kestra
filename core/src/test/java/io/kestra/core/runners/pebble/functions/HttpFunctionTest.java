package io.kestra.core.runners.pebble.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.pebbletemplates.pebble.error.PebbleException;
import jakarta.inject.Inject;
import org.apache.hc.client5.http.utils.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

@MicronautTest
@WireMockTest(httpPort = 28182)
@Execution(ExecutionMode.SAME_THREAD)
class HttpFunctionTest {
    @Inject
    private VariableRenderer variableRenderer;

    @Test
    void defaultHttpCall() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ http(url) }}", Map.of("url", "https://dummyjson.com/todos"));
        Assertions.assertTrue(rendered.startsWith("{\"todos\":[{"));
    }

    @Test
    void postWithBodyHttpCall() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ http(url,'POST',body=body) }}", Map.of(
            "url", "https://dummyjson.com/todos/add",
            "body", Map.of(
                "todo", "New todo",
                "userId", 1,
                "completed", false
            ))
        );
        Assertions.assertTrue(rendered.contains("\"todo\":\"New todo\""));
    }

    @Test
    void wrongMethod() {
        var exception = assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ http(url) }}", Map.of("url", "https://dummyjson.com/todos/add")));
        assertThat(exception.getCause()).isInstanceOf(PebbleException.class);
        assertThat(exception.getCause().getMessage()).isEqualTo("Failed to execute HTTP Request, server respond with status 404 : Not Found ({{ http(url) }}:1)");
    }

    @Test
    void getWithQueryHttpCall() throws IllegalVariableEvaluationException, JsonProcessingException {
        String rendered = variableRenderer.render("""
                {{
                  http(
                    url,
                    'GET',
                    {
                      'limit': 2
                    }
                  )
                }}""", Map.of(
                "url", "https://dummyjson.com/todos"
            )
        );
        Assertions.assertEquals(2, ((List<Map<String, Object>>) JacksonMapper.toMap(rendered).get("todos")).size());
    }

    @Test
    void anotherContentTypeAndAccept(WireMockRuntimeInfo wmRuntimeInfo) throws IllegalVariableEvaluationException {
        String responseBody = """
            response: body
            with:
            - yaml
            - content
            """;
        stubFor(post(urlMatching("/yamlApi"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/x-yaml")
                .withBody(responseBody)));

        // Assert that we receive properly YAML but we also can access the body as an object directly
        String rendered = variableRenderer.render("""
            {{
                http(
                    url,
                    'POST',
                    body=body,
                    contentType='application/yaml',
                    accept='application/yaml'
                ).with[0]
            }}""", Map.of(
            "url", "http://localhost:28182/yamlApi",
            "body", Map.of(
                "request", "body",
                "with", List.of("yaml", "content")
            ))
        );

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        String receivedRequestBody = wireMock.getServeEvents().getFirst().getRequest().getBodyAsString();
        Assertions.assertTrue(receivedRequestBody.contains("request: body"));
        Assertions.assertTrue(receivedRequestBody.contains("""
            with:
            - yaml
            - content"""));

        Assertions.assertEquals("yaml", rendered);
    }

    @Test
    void withHeaders() throws IllegalVariableEvaluationException {
        stubFor(post(urlMatching("/withHeadersApi"))
            .withHeader("custom-header", equalTo("custom-value"))
            .withHeader("multiple-value-header", havingExactly("value1", "value2"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"headers":"received"}"""
                )));

        String rendered = variableRenderer.render("""
                {{
                    http(
                        url,
                        'POST',
                        headers={
                            'custom-header': 'custom-value',
                            'multiple-value-header': ['value1', 'value2']
                        }
                    )
                }}""", Map.of(
                "url", "http://localhost:28182/withHeadersApi"
            )
        );

        Assertions.assertEquals("{\"headers\":\"received\"}", rendered);
    }

    @Test
    void withOptions() throws IllegalVariableEvaluationException {
        stubFor(get(urlMatching("/withBasicAuthApi"))
            .withHeader("Authorization", equalTo("Basic " + Base64.encodeBase64String("myUser:myPassword".getBytes(StandardCharsets.UTF_8))))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"successfully":"authenticated"}"""
                )));

        String rendered = variableRenderer.render("""
                {{
                    http(
                        url,
                        'GET',
                        options={
                            'basicAuthUser': user,
                            'basicAuthPassword': password
                        }
                    )
                }}""", Map.of(
                "url", "http://localhost:28182/withBasicAuthApi",
                "user", "myUser",
                "password", "myPassword"
            )
        );

        Assertions.assertEquals("{\"successfully\":\"authenticated\"}", rendered);
    }
}
