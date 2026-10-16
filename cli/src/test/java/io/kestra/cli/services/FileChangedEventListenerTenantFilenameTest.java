package io.kestra.cli.services;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.GenericFlow;

import static org.assertj.core.api.Assertions.assertThat;

class FileChangedEventListenerTenantFilenameTest {
    @Test
    void shouldPreserveUnderscoresInTenantIdWhenFilenameMatchesFlow() {
        GenericFlow flow = GenericFlow.builder().namespace("my_app").id("service_health").build();

        assertThat(FileChangedEventListener.getTenantIdFromPath(Path.of("my_team_my_app.service_health.yml"), flow)).isEqualTo("my_team");
        assertThat(FileChangedEventListener.getTenantIdFromPath(Path.of("my_build_team_my_app.service_health.yaml"), flow)).isEqualTo("my_build_team");
        assertThat(FileChangedEventListener.getTenantIdFromPath(Path.of("my_team_my_app_service_health.yml"), flow)).isEqualTo("my_team");
        assertThat(FileChangedEventListener.getTenantIdFromPath(Path.of("other_my_app.service_health.yml"), flow)).isEqualTo("other");
    }
}
