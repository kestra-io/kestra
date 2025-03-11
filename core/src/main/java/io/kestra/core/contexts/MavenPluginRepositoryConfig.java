package io.kestra.core.contexts;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Nullable;
import lombok.Builder;

@Builder
@EachProperty("kestra.plugins.repositories")
public record MavenPluginRepositoryConfig(
    @Parameter
    String id,
    String url,
    @Nullable
    BasicAuth basicAuth
) {

    @Builder
    @ConfigurationProperties("basic-auth")
    public record BasicAuth(
        String username,
        String password
    ) {

    }
}
