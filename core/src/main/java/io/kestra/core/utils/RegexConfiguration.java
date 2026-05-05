package io.kestra.core.utils;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for regex timeout. Applies to all user-supplied regex operations
 * (Pebble filters, input validators, storage split, etc.).
 */
@Getter
@Setter
@ConfigurationProperties("kestra.regex")
public class RegexConfiguration {

    /**
     * Maximum duration for a single regex operation before it is aborted.
     * Defaults to 30 seconds.
     */
    private Duration timeout = Duration.ofSeconds(10);

    @PostConstruct
    void init() {
        RegexUtils.setTimeout(timeout);
    }
}
