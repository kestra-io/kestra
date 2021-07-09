package io.kestra.jdbc;

import io.micronaut.context.annotation.Factory;
import org.jooq.conf.*;

import javax.inject.Singleton;

@Factory
public class JooqSettings {
    @Singleton
    public Settings settings() {
        return new Settings()
            .withRenderKeywordCase(RenderKeywordCase.UPPER)
            .withRenderFormatted(true)
            .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_QUOTED)
            .withFetchWarnings(true);
    }
}
