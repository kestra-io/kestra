package io.kestra.core.utils;

import io.kestra.core.repositories.SettingRepositoryInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class VersionProviderTest {
    @Inject
    private VersionProvider versionProvider;

    @Inject
    private SettingRepositoryInterface settingRepository;

    @Test
    void shouldResolveVersion() {
        assertThat(versionProvider.getVersion()).isNotNull();
    }
}