package io.kestra.core.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.utils.VersionProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
        versionService = new VersionService(() -> settingRepository, () -> versionProvider);
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
        assertThat(savedValue(Setting.INSTANCE_VERSION)).isEqualTo(newVersion);
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
        assertThat(savedValue(Setting.INSTANCE_VERSION)).isEqualTo(releaseVersion);
    }

    @Test
    void shouldThrowForTooOldVersion() {
        // Given
        String oldVersion = "0.22.3";
        String newVersion = "2.0.0";
        Setting existingSetting = Setting.builder()
            .key(Setting.INSTANCE_VERSION)
            .value(oldVersion)
            .build();
        when(settingRepository.findByKey(Setting.INSTANCE_VERSION)).thenReturn(Optional.of(existingSetting));
        when(versionProvider.getVersion()).thenReturn(newVersion);

        // When
        var exception = assertThrows(IllegalStateException.class, () -> versionService.maybeSaveOrUpdateInstanceVersion());

        // Then
        assertThat(exception.getMessage()).isEqualTo("Instance version 0.22.3 is too old and cannot be migrated to 2.0.0, please upgrade to at least 1.0.0 first");
    }

    @Test
    void shouldRecordPreviousVersionOnUpgrade() {
        // Given
        givenInstanceVersion("1.3.4");
        when(versionProvider.getVersion()).thenReturn("2.0.0");

        // When
        versionService.maybeSaveOrUpdateInstanceVersion();

        // Then
        assertThat(savedValue(Setting.INSTANCE_PREVIOUS_VERSION))
            .isInstanceOfSatisfying(Map.class, value -> assertThat(value).containsEntry("version", "1.3.4"));
    }

    @Test
    void shouldNotRecordPreviousVersionOnFreshInstall() {
        // Given
        when(settingRepository.findByKey(Setting.INSTANCE_VERSION)).thenReturn(Optional.empty());
        when(versionProvider.getVersion()).thenReturn("2.0.0");

        // When
        versionService.maybeSaveOrUpdateInstanceVersion();

        // Then
        verify(settingRepository).save(settingCaptor.capture());
        assertThat(settingCaptor.getValue().getKey()).isEqualTo(Setting.INSTANCE_VERSION);
    }

    @Test
    void shouldReportNoticeOnMajorUpgrade() {
        // Given
        givenUpgradeFrom("1.3.4", Instant.now());
        when(versionProvider.getVersion()).thenReturn("2.0.0");

        // When / Then
        assertThat(versionService.pendingUpgradeNotice())
            .hasValueSatisfying(notice -> {
                assertThat(notice.from()).isEqualTo("1.3.4");
                assertThat(notice.to()).isEqualTo("2.0.0");
            });
    }

    @Test
    void shouldReportNoticeOnMinorUpgrade() {
        // Given
        givenUpgradeFrom("2.0.3", Instant.now());
        when(versionProvider.getVersion()).thenReturn("2.1.0");

        // When / Then
        assertThat(versionService.pendingUpgradeNotice())
            .hasValueSatisfying(notice -> assertThat(notice.to()).isEqualTo("2.1.0"));
    }

    @Test
    void shouldReportNoticeOnAnyPatchOfANewMinorLine() {
        // What a backport to releases/v2.0.x actually runs: the notice fires for 2.0.5, not just 2.0.0.
        givenUpgradeFrom("1.3.4", Instant.now());
        when(versionProvider.getVersion()).thenReturn("2.0.5");

        assertThat(versionService.pendingUpgradeNotice())
            .hasValueSatisfying(notice -> assertThat(notice.to()).isEqualTo("2.0.5"));
    }

    @Test
    void shouldReportNoNoticeOnPatchUpgrade() {
        // Given
        givenUpgradeFrom("2.0.0", Instant.now());
        when(versionProvider.getVersion()).thenReturn("2.0.1");

        // When / Then
        assertThat(versionService.pendingUpgradeNotice()).isEmpty();
    }

    @Test
    void shouldReportNoNoticeWhenLeavingAReleaseCandidate() {
        // Given a 2.0.0-rc13 to 2.0.0 move: the version moves forward but nothing migrates.
        givenUpgradeFrom("2.0.0-rc13", Instant.now());
        when(versionProvider.getVersion()).thenReturn("2.0.0");

        // When / Then
        assertThat(versionService.pendingUpgradeNotice()).isEmpty();
    }

    @Test
    void shouldReportNoNoticeOnDowngrade() {
        // Given
        givenUpgradeFrom("2.1.0", Instant.now());
        when(versionProvider.getVersion()).thenReturn("2.0.0");

        // When / Then
        assertThat(versionService.pendingUpgradeNotice()).isEmpty();
    }

    @Test
    void shouldReportNoNoticeWhenAVersionIsUnparsable() {
        // Given a development build, where VersionProvider falls back to "Snapshot".
        givenUpgradeFrom("1.3.4", Instant.now());
        when(versionProvider.getVersion()).thenReturn("Snapshot");

        // When / Then
        assertThat(versionService.pendingUpgradeNotice()).isEmpty();
    }

    @Test
    void shouldReportNoNoticeOnceTheUpgradeIsOld() {
        // Given
        givenUpgradeFrom("1.3.4", Instant.now().minus(31, ChronoUnit.DAYS));

        // When / Then
        assertThat(versionService.pendingUpgradeNotice()).isEmpty();
    }

    @Test
    void shouldReportNoNoticeOnFreshInstall() {
        // Given no previous version was ever recorded.
        when(settingRepository.findByKey(Setting.INSTANCE_PREVIOUS_VERSION)).thenReturn(Optional.empty());

        // When / Then
        assertThat(versionService.pendingUpgradeNotice()).isEmpty();
    }

    private void givenInstanceVersion(String version) {
        when(settingRepository.findByKey(Setting.INSTANCE_VERSION)).thenReturn(Optional.of(
            Setting.builder().key(Setting.INSTANCE_VERSION).value(version).build()
        ));
    }

    private void givenUpgradeFrom(String previousVersion, Instant upgradedAt) {
        when(settingRepository.findByKey(Setting.INSTANCE_PREVIOUS_VERSION)).thenReturn(Optional.of(
            Setting.builder()
                .key(Setting.INSTANCE_PREVIOUS_VERSION)
                .value(Map.of("version", previousVersion, "upgradedAt", upgradedAt.toString()))
                .build()
        ));
    }

    private Object savedValue(String key) {
        verify(settingRepository, atLeastOnce()).save(settingCaptor.capture());
        List<Setting> saved = settingCaptor.getAllValues().stream().filter(s -> s.getKey().equals(key)).toList();
        assertThat(saved).as("a setting saved under %s", key).hasSize(1);
        return saved.getFirst().getValue();
    }
}
