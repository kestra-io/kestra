package io.kestra.jdbc;

import org.jooq.conf.RenderKeywordCase;
import org.jooq.conf.Settings;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class JooqSettings {
    @Singleton
    public Settings settings() {
        return new Settings()
            .withRenderKeywordCase(RenderKeywordCase.UPPER)
            .withRenderFormatted(true)
            .withFetchWarnings(true);
    }
}
