package io.kestra.core.reporter.reports;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class SystemInformationReportTest {

    @Inject
    private SystemInformationReport systemInformationReport;

    @Test
    void shouldGetReport() {
        SystemInformationReport.SystemInformationEvent event = systemInformationReport.report(Instant.now());
        assertThat(event.uri()).isEqualTo("https://mysuperhost.com/subpath");
        assertThat(event.environments()).contains("test");
        assertThat(event.startTime()).isNotNull();
        assertThat(event.host().getUuid()).isNotNull();
        assertThat(event.host().getHardware().getLogicalProcessorCount()).isNotNull();
        assertThat(event.host().getJvm().getName()).isNotNull();
        assertThat(event.host().getOs().getFamily()).isNotNull();
        assertThat(event.configurations().getRepositoryType()).isEqualTo("h2");
        assertThat(event.configurations().getQueueType()).isEqualTo("h2");
    }

    @MockBean(SettingRepositoryInterface.class)
    @Singleton
    static class TestSettingRepository implements SettingRepositoryInterface {
        public static Object UUID = null;

        @Override
        public Optional<Setting> findByKey(String key) {
            return Optional.empty();
        }

        @Override
        public List<Setting> findAll() {
            return new ArrayList<>();
        }

        @Override
        public Setting save(Setting setting) throws ConstraintViolationException {
            if (setting.getKey().equals(Setting.INSTANCE_UUID)) {
                UUID = setting.getValue();
            }

            return setting;
        }

        @Override
        public Setting internalSave(Setting setting) throws ConstraintViolationException {
            if (setting.getKey().equals(Setting.INSTANCE_UUID)) {
                UUID = setting.getValue();
            }

            return setting;
        }

        @Override
        public Setting delete(Setting setting) {
            return setting;
        }
    }
}