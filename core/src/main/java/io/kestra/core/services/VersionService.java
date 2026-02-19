package io.kestra.core.services;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.utils.VersionProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Service responsible for managing the version information of the Kestra instance.
 * <p>
 * It interacts with the settings repository to store and retrieve the instance version.
 */
@Singleton
@Slf4j
public class VersionService {

    private final SettingRepositoryInterface settingRepository;
    private final VersionProvider versionProvider;

    /**
     * Creates a new {@link VersionService} instance.
     *
     * @param settingRepository the repository to manage settings.
     */
    @Inject
    public VersionService(final SettingRepositoryInterface settingRepository,
                          final VersionProvider versionProvider) {
        this.settingRepository = settingRepository;
        this.versionProvider = versionProvider;
    }

    /**
     * Retrieves the current instance version from the settings repository.
     *
     * @return an {@link Optional} containing the instance version if it exists, or an empty {@link Optional} if it does not.
     */
    public Optional<String> getInstanceVersion() {
        return settingRepository.findByKey(Setting.INSTANCE_VERSION).map(Setting::getValue).map(Object::toString);
    }

    /**
     * Checks if the current instance version is stored in the settings repository and saves
     * it if it's not present or if it differs from the software version.
     */
    public void maybeSaveOrUpdateInstanceVersion() {
        Optional<String> settingVersion = getInstanceVersion();
        final String softwareVersion = versionProvider.getVersion();
        if (settingVersion.isEmpty() || !settingVersion.get().equals(softwareVersion)) {
            log.info("Updating instance version from {} to {}", settingVersion.orElse("none"), softwareVersion);
            settingRepository.save(Setting.builder()
                .key(Setting.INSTANCE_VERSION)
                .value(softwareVersion)
                .build()
            );
        }
    }
}
