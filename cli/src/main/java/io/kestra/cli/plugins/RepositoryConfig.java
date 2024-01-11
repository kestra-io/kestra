package io.kestra.cli.plugins;

import io.kestra.core.models.annotations.PluginProperty;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EachProperty("kestra.plugins.repositories")
@Getter
@AllArgsConstructor
@Builder
public class RepositoryConfig {
    String id;

    @Builder.Default
    String type = "default";

    String url;

    BasicAuth basicAuth;

    @Getter
    @AllArgsConstructor
    public static class BasicAuth {
        private String username;
        private String password;
    }

    public RepositoryConfig(@Parameter String id) {
        this.id = id;
    }
}
