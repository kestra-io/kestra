package io.kestra.core.repositories;

import io.kestra.core.models.Setting;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
        assertThat(find.isPresent(), is(false));

        Setting save = settingRepository.save(setting);

        find = settingRepository.findByKey(save.getKey());

        assertThat(find.isPresent(), is(true));
        assertThat(find.get().getValue(), is(save.getValue()));

        List<Setting> all = settingRepository.findAll();
        assertThat(all.size(), is(1));
        assertThat(all.getFirst().getValue(), is(setting.getValue()));

        Setting delete = settingRepository.delete(setting);
        assertThat(delete.getValue(), is(setting.getValue()));

        all = settingRepository.findAll();
        assertThat(all.size(), is(0));

        find = settingRepository.findByKey(setting.getKey());
        assertThat(find.isPresent(), is(false));
    }
}
