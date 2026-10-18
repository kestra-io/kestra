package io.kestra.plugin.scripts.runner.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.github.dockerjava.core.NameParser;

import static org.assertj.core.api.Assertions.assertThat;

class DockerServiceTest {

    @ParameterizedTest
    @CsvSource(
        {
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
        }
    )
    void normalizeRegistryUrl(String input, String expected) {
        assertThat(DockerService.normalizeRegistryUrl(input)).isEqualTo(expected);
    }

    @Test
    void normalizeRegistryUrl_null() {
        assertThat(DockerService.normalizeRegistryUrl(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource(
        {
            // A bare name pulls the implicit 'latest' tag
            "alpine,                                        alpine,                     latest",
            "cytopia/ansible,                               cytopia/ansible,            latest",

            // An explicit tag is split off the name
            "alpine:3.20,                                   alpine,                     3.20",
            "cytopia/ansible:2.20-tools,                    cytopia/ansible,            2.20-tools",

            // The colon of a registry port is not a tag separator
            "myregistry.example.com:5000/ansible,           myregistry.example.com:5000/ansible, latest",
            "myregistry.example.com:5000/ansible:2.20,      myregistry.example.com:5000/ansible, 2.20",

            // A digest is pulled as the tag, and any tag next to it is redundant
            "cytopia/ansible@sha256:b273f5b1,               cytopia/ansible,            sha256:b273f5b1",
            "cytopia/ansible:2.20-tools@sha256:b273f5b1,    cytopia/ansible,            sha256:b273f5b1",
            "myregistry.example.com:5000/ansible@sha256:b273f5b1,      myregistry.example.com:5000/ansible, sha256:b273f5b1",
            "myregistry.example.com:5000/ansible:2.20@sha256:b273f5b1, myregistry.example.com:5000/ansible, sha256:b273f5b1",

            // Anything after the '@' that is not a digest keeps the separator, so the daemon rejects
            // the reference instead of an empty tag pulling every tag or a bare tag pulling the wrong image
            "cytopia/ansible@,                              cytopia/ansible@,           latest",
            "cytopia/ansible@2.20-tools,                    cytopia/ansible@2.20-tools, latest",
        }
    )
    void shouldSplitRepositoryFromTagOrDigestGivenAnImageReference(String image, String expectedRepository, String expectedTag) {
        assertThat(DockerService.parseImageReference(image))
            .isEqualTo(new NameParser.ReposTag(expectedRepository, expectedTag));
    }
}
