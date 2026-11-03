package io.kestra.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.serializers.JacksonMapper;

import io.micronaut.core.io.ResourceResolver;

import static org.assertj.core.api.Assertions.assertThat;

class OpenapiTest {

    @Test
    void generatedOpenapiSpecFile() {
        Optional<URL> openapiSpec = new ResourceResolver().getResource("classpath:META-INF/swagger/kestra.yml");

        assertThat(openapiSpec.isPresent()).isTrue();
    }

    @Test
    void shouldDocumentTtlAndDescriptionHeadersWhenSettingKeyValue() throws IOException {
        JsonNode parameters = spec().path("paths").path("/api/v1/{tenant}/namespaces/{namespace}/kv/{key}").path("put").path("parameters");

        List<String> headers = new ArrayList<>();
        parameters.forEach(parameter -> {
            if ("header".equals(parameter.path("in").asText())) {
                headers.add(parameter.path("name").asText());
            }
        });
        assertThat(headers).contains("ttl", "description");
    }

    private static JsonNode spec() throws IOException {
        try (InputStream stream = new ResourceResolver().getResourceAsStream("classpath:META-INF/swagger/kestra.yml").orElseThrow()) {
            return JacksonMapper.ofYaml().readTree(stream);
        }
    }
}
