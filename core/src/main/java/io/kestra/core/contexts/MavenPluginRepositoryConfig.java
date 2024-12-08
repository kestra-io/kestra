package io.kestra.core.contexts;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Nullable;
import lombok.Builder;

@EachProperty("kestra.plugins.repositories")
@Builder
public record MavenPluginRepositoryConfig(
    @Parameter
    String id,
    String url,

    @Nullable
    BasicAuth basicAuth
) {


    public record BasicAuth(
        String username,
        String password
    ) {

    }
}
