package io.kestra.webserver;

import io.micronaut.core.io.ResourceResolver;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OpenapiTest {

    @Test
    void generatedOpenapiSpecFile() {
        Optional<URL> openapiSpec = new ResourceResolver().getResource("classpath:META-INF/swagger/kestra.yml");

        assertThat(openapiSpec.isPresent()).isTrue();
    }
}
