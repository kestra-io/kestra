package io.kestra.plugin.scripts.exec.scripts.models;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.plugin.scripts.runner.docker.Docker;

import static org.assertj.core.api.Assertions.assertThat;

class DockerOptionsTest {
    @Test
    void shouldCopyDnsServersToDockerTaskRunner() {
        var dnsServers = List.of("8.8.8.8", "1.1.1.1");
        var options = DockerOptions.builder()
            .image("alpine")
            .dns(dnsServers)
            .build();

        Docker docker = Docker.from(options);

        assertThat(docker.getDns()).isEqualTo(dnsServers);
    }
}
