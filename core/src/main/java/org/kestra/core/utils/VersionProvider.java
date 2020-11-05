package org.kestra.core.utils;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertiesPropertySourceLoader;
import io.micronaut.core.util.StringUtils;
import lombok.Getter;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class VersionProvider {
    @Getter
    private String version = "Snapshot";

    @Inject
    Environment environment;

    @PostConstruct
    public void start() {
        new PropertiesPropertySourceLoader()
                .load("classpath:git", environment)
                .ifPresent(properties -> {

                    this.version = Stream
                        .of(
                            properties.get("git.tags"),
                            properties.get("git.branch")
                        )
                        .map(this::getVersion)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst()
                        .orElse(this.version);
                });
    }

    private Optional<String> getVersion(Object object) {
        String candidate = Objects.toString(object, null);

        return StringUtils.isNotEmpty(candidate) ? Optional.of(candidate) : Optional.empty();
    }
}
