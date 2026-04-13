package io.kestra.core.utils;

import java.util.Optional;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;

import jakarta.inject.Singleton;

@Singleton
public class EditionProvider {
    public Edition get() {
        return Edition.OSS;
    }

    /**
     * Persists the current edition in the settings repository.
     * Must be called after database migrations have completed (e.g., from a startup
     * hook).
     *
     * @param settingRepository the repository to persist the edition setting.
     */
    public void persistEdition(SettingRepositoryInterface settingRepository) {
        Edition edition = get();
        Optional<Setting> editionSetting = settingRepository.findByKey(Setting.INSTANCE_EDITION);
        if (editionSetting.isEmpty() || !editionSetting.get().getValue().equals(edition)) {
            settingRepository.save(
                    Setting.builder()
                            .key(Setting.INSTANCE_EDITION)
                            .value(edition)
                            .build());
        }
    }

    public enum Edition {
        OSS,
        EE
    }
}
