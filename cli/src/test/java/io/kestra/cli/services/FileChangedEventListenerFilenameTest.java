package io.kestra.cli.services;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.GenericFlow;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

class FileChangedEventListenerFilenameTest {
    @Test
    void shouldResolveTenantFromDotSeparatedFilename() {
        GenericFlow reportedFlow = GenericFlow.builder().namespace("myapp").id("service-health").build();
        GenericFlow flowWithUnderscores = GenericFlow.builder().namespace("my_app").id("service_health").build();

        assertThat(FileChangedEventListener.getTenantIdFromPath(Path.of("myapp.service-health.yml"), reportedFlow)).isEqualTo(MAIN_TENANT);
        assertThat(FileChangedEventListener.getTenantIdFromPath(Path.of("my_app.service_health.yaml"), flowWithUnderscores)).isEqualTo(MAIN_TENANT);
        assertThat(FileChangedEventListener.getTenantIdFromPath(Path.of("other_my_app.service_health.yml"), flowWithUnderscores)).isEqualTo("other");
    }
}
