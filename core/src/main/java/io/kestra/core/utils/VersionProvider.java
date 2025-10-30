package io.kestra.core.utils;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertiesPropertySourceLoader;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.util.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Singleton
public class VersionProvider {
    @Getter
    private String version = "Snapshot";

    @Getter
    private String revision;

    @Getter
    private ZonedDateTime date;

    @Inject
    private Environment environment;

    @Inject
    private Optional<SettingRepositoryInterface> settingRepository; // repositories are not always there on unit tests

    @PostConstruct
    public void start() {
        final Optional<PropertySource> gitProperties = new PropertiesPropertySourceLoader()
            .load("classpath:git", environment);

        final Optional<PropertySource> buildProperties = new PropertiesPropertySourceLoader()
            .load("classpath:gradle", environment);

        this.revision = loadRevision(gitProperties);
        this.date = loadTime(gitProperties);
        this.version = loadVersion(buildProperties, gitProperties);

        // check the version in the settings and update if needed, we didn't use it would allow us to detect incompatible update later if needed
        settingRepository.ifPresent(
            settingRepositoryInterface -> persistVersion(settingRepositoryInterface, version));
    }

    private static synchronized void persistVersion(SettingRepositoryInterface settingRepositoryInterface, String version) {
        Optional<Setting> versionSetting = settingRepositoryInterface.findByKey(Setting.INSTANCE_VERSION);
        if (versionSetting.isEmpty() || !versionSetting.get().getValue().equals(version)) {
            settingRepositoryInterface.save(Setting.builder()
                .key(Setting.INSTANCE_VERSION)
                .value(version)
                .build()
            );
        }
    }

    private String loadVersion(final Optional<PropertySource> buildProperties,
                               final Optional<PropertySource> gitProperties) {
        return Stream
            .concat(
                buildProperties
                    .stream()
                    .flatMap(properties -> Stream.of(
                        properties.get("version"))),
                gitProperties
                    .stream()
                    .flatMap(properties -> Stream
                        .of(
                            properties.get("git.tags"),
                            properties.get("git.branch")
                        )
                    )
            )
            .map(this::getVersion)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst()
            .orElse(this.version);
    }

    private String loadRevision(final Optional<PropertySource> gitProperties) {
        return gitProperties
            .stream()
            .flatMap(properties -> Stream
                .of(
                    properties.get("git.commit.id.abbrev"),
                    properties.get("git.commit.id")
                )
            ).findFirst()
            .map(Object::toString)
            .orElse(null);
    }

    private ZonedDateTime loadTime(final Optional<PropertySource> gitProperties) {
        return gitProperties
            .stream()
            .flatMap(properties -> Stream
                .of(
                    properties.get("git.commit.time")
                )
            ).findFirst()
            .map(Object::toString)
            .map(s -> {
                try {
                    return ZonedDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXXX"));
                } catch (Exception e) {
                    return null;
                }
            })
            .orElse(null);
    }

    private Optional<String> getVersion(Object object) {
        String candidate = Objects.toString(object, null);
        return StringUtils.isNotEmpty(candidate) ? Optional.of(candidate) : Optional.empty();
    }
}
