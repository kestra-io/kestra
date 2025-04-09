package io.kestra.core.repositories;

import io.kestra.core.models.Setting;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public abstract class AbstractSettingRepositoryTest {
    @Inject
    protected SettingRepositoryInterface settingRepository;

    @Test
    void all() {
        Setting setting = Setting.builder()
            .key(Setting.INSTANCE_UUID)
            .value(IdUtils.create())
            .build();

        Optional<Setting> find = settingRepository.findByKey(setting.getKey());
        assertThat(find.isPresent()).isEqualTo(false);

        Setting save = settingRepository.save(setting);

        find = settingRepository.findByKey(save.getKey());

        assertThat(find.isPresent()).isEqualTo(true);
        assertThat(find.get().getValue()).isEqualTo(save.getValue());

        List<Setting> all = settingRepository.findAll();
        assertThat(all.size()).isEqualTo(1);
        assertThat(all.getFirst().getValue()).isEqualTo(setting.getValue());

        Setting delete = settingRepository.delete(setting);
        assertThat(delete.getValue()).isEqualTo(setting.getValue());

        all = settingRepository.findAll();
        assertThat(all.size()).isEqualTo(0);

        find = settingRepository.findByKey(setting.getKey());
        assertThat(find.isPresent()).isEqualTo(false);
    }
}
