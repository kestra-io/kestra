package io.kestra.plugin.scripts.runner.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DockerServiceTest {

    @ParameterizedTest
    @CsvSource({
        // Docker Hub v2 URLs must map to the canonical v1 key
        "https://registry-1.docker.io/v2/,  https://index.docker.io/v1/",
        "https://registry-1.docker.io/v2,   https://index.docker.io/v1/",
        "registry-1.docker.io/v2/,           https://index.docker.io/v1/",
        "registry-1.docker.io/v2,            https://index.docker.io/v1/",
        "registry-1.docker.io,               https://index.docker.io/v1/",
        "https://registry-1.docker.io,       https://index.docker.io/v1/",
        "http://registry-1.docker.io/v2/,    https://index.docker.io/v1/",

        // Docker Hub canonical v1 URL preserved
        "https://index.docker.io/v1/,        https://index.docker.io/v1/",
        "https://index.docker.io/v1,         https://index.docker.io/v1/",
        "index.docker.io/v1,                 https://index.docker.io/v1/",
        "https://index.docker.io/v2/,        https://index.docker.io/v1/",
        "index.docker.io,                    https://index.docker.io/v1/",

        // Other registries: strip /v2/ suffix
        "https://ghcr.io/v2/,               https://ghcr.io",
        "https://ghcr.io/v2,                https://ghcr.io",
        "myregistry.example.com/v2/,         myregistry.example.com",
        "myregistry.example.com/v2,          myregistry.example.com",

        // Other registries without /v2 are left unchanged (trailing slash stripped)
        "https://ghcr.io,                    https://ghcr.io",
        "myregistry.example.com,             myregistry.example.com",
        "https://123456789.dkr.ecr.us-east-1.amazonaws.com, https://123456789.dkr.ecr.us-east-1.amazonaws.com",
    })
    void normalizeRegistryUrl(String input, String expected) {
        assertThat(DockerService.normalizeRegistryUrl(input)).isEqualTo(expected);
    }

    @Test
    void normalizeRegistryUrl_null() {
        assertThat(DockerService.normalizeRegistryUrl(null)).isNull();
    }
}
