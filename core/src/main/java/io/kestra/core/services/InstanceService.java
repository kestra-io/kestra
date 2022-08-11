package io.kestra.core.services;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class InstanceService {
    @Inject
    private SettingRepositoryInterface settingRepository;

    private Setting instanceIdSetting;

    public String fetch() {
        if (this.instanceIdSetting == null) {
            instanceIdSetting = settingRepository
                .findByKey(Setting.INSTANCE_UUID)
                .orElseGet(() -> settingRepository.save(Setting.builder()
                    .key(Setting.INSTANCE_UUID)
                    .value(IdUtils.create())
                    .build()
                ));
        }

        return this.instanceIdSetting.getValue().toString();
    }
}
