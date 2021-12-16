package io.kestra.storage.local;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

import jakarta.inject.Singleton;
import java.nio.file.Path;

@Singleton
@Getter
@ConfigurationProperties("kestra.storage.local")
public class LocalConfig {
    Path basePath;
}