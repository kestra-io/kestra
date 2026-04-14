package io.kestra.core.services;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Singleton
@Slf4j
public class InstanceService {

    private final SettingRepositoryInterface settingRepository;

    @Inject
    public InstanceService(Optional<SettingRepositoryInterface> settingRepository) {
        this.settingRepository = settingRepository.orElse(null);
    }

    private volatile Setting instanceIdSetting;

    public String fetch() {
        if (this.instanceIdSetting == null) {
            synchronized (this) {
                if (this.instanceIdSetting == null) {
                    instanceIdSetting = fetchInstanceUuid();
                }
            }
        }
        return this.instanceIdSetting.getValue().toString();
    }

    private Setting fetchInstanceUuid() {
        return settingRepository
            .findByKey(Setting.INSTANCE_UUID)
            .orElseGet(
                () -> settingRepository.save(
                    Setting.builder()
                        .key(Setting.INSTANCE_UUID)
                        .value(IdUtils.create())
                        .build()
                )
            );
    }
}
