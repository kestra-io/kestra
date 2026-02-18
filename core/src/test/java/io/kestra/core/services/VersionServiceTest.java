package io.kestra.core.services;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.utils.VersionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @Mock
    private SettingRepositoryInterface settingRepository;

    @Mock
    private VersionProvider versionProvider;

    @Captor
    private ArgumentCaptor<Setting> settingCaptor;

    private VersionService versionService;

    @BeforeEach
    void setUp() {
        versionService = new VersionService(settingRepository, versionProvider);
    }

    @Test
    void shouldReturnInstanceVersionWhenPresent() {
        // Given
        Setting setting = Setting.builder()
            .key(Setting.INSTANCE_VERSION)
            .value("1.2.3")
            .build();
        when(settingRepository.findByKey(Setting.INSTANCE_VERSION)).thenReturn(Optional.of(setting));

        // When
        Optional<String> version = versionService.getInstanceVersion();

        // Then
        assertThat(version).isPresent();
        assertThat(version.get()).isEqualTo("1.2.3");
        verify(settingRepository).findByKey(Setting.INSTANCE_VERSION);
    }

    @Test
    void shouldReturnEmptyOptionalWhenVersionNotPresent() {
        // Given
        when(settingRepository.findByKey(Setting.INSTANCE_VERSION)).thenReturn(Optional.empty());

        // When
        Optional<String> version = versionService.getInstanceVersion();

        // Then
        assertThat(version).isEmpty();
        verify(settingRepository).findByKey(Setting.INSTANCE_VERSION);
    }

    @Test
    void shouldSaveVersionWhenNotPresent() {
        // Given
        String softwareVersion = "1.2.3";
        when(settingRepository.findByKey(Setting.INSTANCE_VERSION)).thenReturn(Optional.empty());
        when(versionProvider.getVersion()).thenReturn(softwareVersion);

        // When
        versionService.maybeSaveOrUpdateInstanceVersion();

        // Then
        verify(settingRepository).save(settingCaptor.capture());
        Setting savedSetting = settingCaptor.getValue();
        assertThat(savedSetting.getKey()).isEqualTo(Setting.INSTANCE_VERSION);
        assertThat(savedSetting.getValue()).isEqualTo(softwareVersion);
    }

    @Test
    void shouldUpdateVersionWhenDifferent() {
        // Given
        String oldVersion = "1.2.3";
        String newVersion = "1.2.4";
        Setting existingSetting = Setting.builder()
            .key(Setting.INSTANCE_VERSION)
            .value(oldVersion)
            .build();
        when(settingRepository.findByKey(Setting.INSTANCE_VERSION)).thenReturn(Optional.of(existingSetting));
        when(versionProvider.getVersion()).thenReturn(newVersion);

        // When
        versionService.maybeSaveOrUpdateInstanceVersion();

        // Then
        verify(settingRepository).save(settingCaptor.capture());
        Setting savedSetting = settingCaptor.getValue();
        assertThat(savedSetting.getKey()).isEqualTo(Setting.INSTANCE_VERSION);
        assertThat(savedSetting.getValue()).isEqualTo(newVersion);
    }

    @Test
    void shouldNotSaveVersionWhenAlreadyUpToDate() {
        // Given
        String currentVersion = "1.2.3";
        Setting existingSetting = Setting.builder()
            .key(Setting.INSTANCE_VERSION)
            .value(currentVersion)
            .build();
        when(settingRepository.findByKey(Setting.INSTANCE_VERSION)).thenReturn(Optional.of(existingSetting));
        when(versionProvider.getVersion()).thenReturn(currentVersion);

        // When
        versionService.maybeSaveOrUpdateInstanceVersion();

        // Then
        verify(settingRepository, never()).save(any(Setting.class));
    }

    @Test
    void shouldHandleNumericVersionValue() {
        // Given - version stored as Integer instead of String
        Setting setting = Setting.builder()
            .key(Setting.INSTANCE_VERSION)
            .value(123)
            .build();
        when(settingRepository.findByKey(Setting.INSTANCE_VERSION)).thenReturn(Optional.of(setting));

        // When
        Optional<String> version = versionService.getInstanceVersion();

        // Then
        assertThat(version).isPresent();
        assertThat(version.get()).isEqualTo("123");
        verify(settingRepository).findByKey(Setting.INSTANCE_VERSION);
    }

    @Test
    void shouldSaveSnapshotVersion() {
        // Given
        String snapshotVersion = "1.3.0-SNAPSHOT";
        when(settingRepository.findByKey(Setting.INSTANCE_VERSION)).thenReturn(Optional.empty());
        when(versionProvider.getVersion()).thenReturn(snapshotVersion);

        // When
        versionService.maybeSaveOrUpdateInstanceVersion();

        // Then
        verify(settingRepository).save(settingCaptor.capture());
        Setting savedSetting = settingCaptor.getValue();
        assertThat(savedSetting.getKey()).isEqualTo(Setting.INSTANCE_VERSION);
        assertThat(savedSetting.getValue()).isEqualTo(snapshotVersion);
    }

    @Test
    void shouldUpdateFromSnapshotToReleaseVersion() {
        // Given
        String snapshotVersion = "1.3.0-SNAPSHOT";
        String releaseVersion = "1.3.0";
        Setting existingSetting = Setting.builder()
            .key(Setting.INSTANCE_VERSION)
            .value(snapshotVersion)
            .build();
        when(settingRepository.findByKey(Setting.INSTANCE_VERSION)).thenReturn(Optional.of(existingSetting));
        when(versionProvider.getVersion()).thenReturn(releaseVersion);

        // When
        versionService.maybeSaveOrUpdateInstanceVersion();

        // Then
        verify(settingRepository).save(settingCaptor.capture());
        Setting savedSetting = settingCaptor.getValue();
        assertThat(savedSetting.getKey()).isEqualTo(Setting.INSTANCE_VERSION);
        assertThat(savedSetting.getValue()).isEqualTo(releaseVersion);
    }
}
