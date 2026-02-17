package io.kestra.core.utils;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class EditionProvider {
    public Edition get() {
        return Edition.OSS;
    }

    @Inject
    private Optional<SettingRepositoryInterface> settingRepository; // repositories are not always there on unit tests

    @PostConstruct
    void start() {
        // check the edition in the settings and update if needed, we didn't use it would allow us to detect incompatible update later if needed
        settingRepository.ifPresent(settingRepositoryInterface -> persistEdition(settingRepositoryInterface, get()));
    }

    private void persistEdition(SettingRepositoryInterface settingRepositoryInterface, Edition edition) {
        Optional<Setting> versionSetting = settingRepositoryInterface.findByKey(Setting.INSTANCE_EDITION);
        if (versionSetting.isEmpty() || !versionSetting.get().getValue().equals(edition)) {
            settingRepositoryInterface.save(Setting.builder()
                .key(Setting.INSTANCE_EDITION)
                .value(edition)
                .build()
            );
        }
    }

    public enum Edition {
        OSS,
        EE
    }
}
