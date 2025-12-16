package io.kestra.core.utils;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class VersionProviderTest {
    @Inject
    private VersionProvider versionProvider;

    @Inject
    private SettingRepositoryInterface settingRepository;

    @Test
    void shouldResolveVersion() {
        assertThat(versionProvider.getVersion()).endsWith("-SNAPSHOT");

        // check that the version is persisted in settings
        Optional<Setting> versionSettings = settingRepository.findByKey(Setting.INSTANCE_VERSION);
        assertThat(versionSettings).isPresent();
        assertThat(versionSettings.get().getValue()).isEqualTo(versionProvider.getVersion());
    }
}