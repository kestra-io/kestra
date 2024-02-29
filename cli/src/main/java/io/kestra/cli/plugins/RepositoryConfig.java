package io.kestra.cli.plugins;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@EachProperty("kestra.plugins.repositories")
@Getter
@AllArgsConstructor
@Builder
public class RepositoryConfig {
    String id;

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
