package io.kestra.webserver.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

@ConfigurationProperties("kestra.webserver")
public record WebserverConfiguration(
    @Nullable String googleAnalytics,
    @Nullable String htmlTitle,
    @Nullable String htmlHead) {
}
