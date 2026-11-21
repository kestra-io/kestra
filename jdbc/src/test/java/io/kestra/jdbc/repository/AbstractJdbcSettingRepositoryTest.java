package io.kestra.jdbc.repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.AlreadyExistsException;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.AbstractSettingRepositoryTest;
import io.kestra.core.utils.IdUtils;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest(transactional = false)
public abstract class AbstractJdbcSettingRepositoryTest extends AbstractSettingRepositoryTest {
    @Inject
    private AbstractJdbcSettingRepository jdbcSettingRepository;

    @Test
    void shouldThrowAlreadyExistsWhenCreatingAnExistingSetting() {
        Setting setting = setting();
        jdbcSettingRepository.create(setting);

        try {
            assertThatThrownBy(() -> jdbcSettingRepository.create(setting)).isInstanceOf(AlreadyExistsException.class);
        } finally {
            jdbcSettingRepository.purge(setting);
        }
    }

    @Test
    void shouldPublishEventsOnUpdateAndDeleteButNotOnSave() {
        Setting setting = setting();

        jdbcSettingRepository.internalSave(setting);
        assertThat(SettingListener.filterByKey(setting.getKey())).isEmpty();

        jdbcSettingRepository.save(setting);
        jdbcSettingRepository.delete(setting);

        assertThat(SettingListener.filterByKey(setting.getKey()))
            .extracting(CrudEvent::getType)
            .containsExactly(CrudEventType.UPDATE, CrudEventType.DELETE);
    }

    private static Setting setting() {
        return Setting.builder()
            .key("test-" + IdUtils.create())
            .value(IdUtils.create())
            .build();
    }

    @Singleton
    public static class SettingListener implements ApplicationEventListener<CrudEvent<Setting>> {
        private static final List<CrudEvent<Setting>> emits = new CopyOnWriteArrayList<>();

        @Override
        public void onApplicationEvent(CrudEvent<Setting> event) {
            if (event.getModel() instanceof Setting || event.getPreviousModel() instanceof Setting) {
                emits.add(event);
            }
        }

        public static List<CrudEvent<Setting>> filterByKey(String key) {
            return emits.stream()
                .filter(
                    e -> (e.getModel() != null && key.equals(e.getModel().getKey())) ||
                        (e.getPreviousModel() != null && key.equals(e.getPreviousModel().getKey()))
                )
                .toList();
        }
    }
}
