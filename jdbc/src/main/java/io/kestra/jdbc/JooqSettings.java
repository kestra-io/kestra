package io.kestra.jdbc;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import org.jooq.conf.RenderKeywordCase;
import org.jooq.conf.Settings;

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
