package io.kestra.core.services;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.utils.Version;
import io.kestra.core.utils.VersionProvider;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for managing the version information of the Kestra instance.
 * <p>
 * It interacts with the settings repository to store and retrieve the instance version.
 */
@Singleton
@Slf4j
public class VersionService {
    private static final String MIN_VERSION = "1.0.0";

    /**
     * How long after an upgrade {@link #pendingUpgradeNotice()} keeps reporting it, so that a user who
     * joins the instance long afterwards is not told about a migration that never concerned them.
     */
    private static final Duration UPGRADE_NOTICE_WINDOW = Duration.ofDays(30);

    private static final String PREVIOUS_VERSION_KEY = "version";
    private static final String UPGRADED_AT_KEY = "upgradedAt";

    private final Provider<SettingRepositoryInterface> settingRepository;
    private final Provider<VersionProvider> versionProvider;

    /**
     * Creates a new {@link VersionService} instance.
     *
     * @param settingRepository the repository to manage settings.
     */
    @Inject
    public VersionService(final Provider<SettingRepositoryInterface> settingRepository,
        final Provider<VersionProvider> versionProvider) {
        this.settingRepository = settingRepository;
        this.versionProvider = versionProvider;
    }

    /**
     * Retrieves the current instance version from the settings repository.
     *
     * @return an {@link Optional} containing the instance version if it exists, or an empty {@link Optional} if it does not.
     */
    public Optional<String> getInstanceVersion() {
        return settingRepository.get().findByKey(Setting.INSTANCE_VERSION).map(Setting::getValue).map(Object::toString);
    }

    /**
     * Checks if the current instance version is stored in the settings repository and saves
     * it if it's not present or if it differs from the software version.
     */
    public void maybeSaveOrUpdateInstanceVersion() {
        Optional<String> settingVersion = getInstanceVersion();
        final String softwareVersion = versionProvider.get().getVersion();
        if (settingVersion.isEmpty() || !settingVersion.get().equals(softwareVersion)) {
            // check that the settings version is not too old for supporting the migration
            // the check is basic: it will work up to version 10.0.0...
            if (settingVersion.isPresent()) {
                if (settingVersion.get().compareTo(MIN_VERSION) < 0) {
                    throw new IllegalStateException(
                        String.format(
                            "Instance version %s is too old and cannot be migrated to %s, please upgrade to at least %s first",
                            settingVersion.get(),
                            softwareVersion,
                            MIN_VERSION
                        )
                    );
                }
            }

            log.info("Updating instance version from {} to {}", settingVersion.orElse("none"), softwareVersion);

            // The version being replaced is only knowable here, so record it before it is overwritten.
            // Nothing is recorded on a fresh install, which is what keeps the notice from firing there.
            settingVersion.ifPresent(previous -> settingRepository.get().save(
                Setting.builder()
                    .key(Setting.INSTANCE_PREVIOUS_VERSION)
                    .value(Map.of(
                        PREVIOUS_VERSION_KEY, previous,
                        UPGRADED_AT_KEY, Instant.now().toString()
                    ))
                    .build()
            ));

            settingRepository.get().save(
                Setting.builder()
                    .key(Setting.INSTANCE_VERSION)
                    .value(softwareVersion)
                    .build()
            );
        }
    }

    /**
     * Returns the major or minor upgrade this instance recently went through, if users should still be
     * told about it.
     * <p>
     * Empty when the instance was freshly installed, when only the patch version moved, when the version
     * moved backwards, when either version is unparsable (a development build reports {@code Snapshot}),
     * or when the upgrade is older than {@link #UPGRADE_NOTICE_WINDOW}.
     *
     * @return the upgrade worth reporting, or empty.
     */
    public Optional<VersionUpgrade> pendingUpgradeNotice() {
        Optional<Setting> setting = settingRepository.get().findByKey(Setting.INSTANCE_PREVIOUS_VERSION);
        if (setting.isEmpty() || !(setting.get().getValue() instanceof Map<?, ?> value)) {
            return Optional.empty();
        }

        Object from = value.get(PREVIOUS_VERSION_KEY);
        Object at = value.get(UPGRADED_AT_KEY);
        if (from == null || at == null) {
            return Optional.empty();
        }

        final Instant upgradedAt;
        try {
            upgradedAt = Instant.parse(at.toString());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }

        if (upgradedAt.isBefore(Instant.now().minus(UPGRADE_NOTICE_WINDOW))) {
            return Optional.empty();
        }

        final String to = versionProvider.get().getVersion();
        final Version previous;
        final Version current;
        try {
            previous = Version.of(from.toString());
            current = Version.of(to);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        if (!previous.isBefore(current)) {
            return Optional.empty();
        }

        boolean sameMajorAndMinor = previous.majorVersion() == current.majorVersion()
            && previous.minorVersion() == current.minorVersion();
        if (sameMajorAndMinor) {
            return Optional.empty();
        }

        return Optional.of(new VersionUpgrade(from.toString(), to, upgradedAt));
    }

    /**
     * A recent major or minor upgrade of the instance.
     *
     * @param from the version the instance ran before the upgrade.
     * @param to the version it runs now.
     * @param at when the upgrade was first observed.
     */
    public record VersionUpgrade(String from, String to, Instant at) {
    }
}
