package io.kestra.core.repositories;

import io.kestra.core.models.Setting;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public abstract class AbstractSettingRepositoryTest {
    @Inject
    protected SettingRepositoryInterface settingRepository;

    // will make sure the settings for version is created
    @Inject
    protected VersionProvider versionProvider;

    @Test
    void all() {
        Setting setting = Setting.builder()
            .key(Setting.INSTANCE_UUID)
            .value(IdUtils.create())
            .build();

        Optional<Setting> find = settingRepository.findByKey(setting.getKey());
        assertThat(find.isPresent()).isFalse();

        Setting save = settingRepository.save(setting);

        find = settingRepository.findByKey(save.getKey());

        assertThat(find.isPresent()).isTrue();
        assertThat(find.get().getValue()).isEqualTo(save.getValue());

        List<Setting> all = settingRepository.findAll();
        assertThat(all.size()).isGreaterThanOrEqualTo(1); // ES have the version setting in test but not JDBC I don't know why
        assertThat(all)
            .extracting(Setting::getValue)
            .contains(setting.getValue());

        Setting delete = settingRepository.delete(setting);
        assertThat(delete.getValue()).isEqualTo(setting.getValue());

        find = settingRepository.findByKey(setting.getKey());
        assertThat(find.isPresent()).isFalse();
    }
}
