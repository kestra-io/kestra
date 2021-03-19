package io.kestra.cli.plugins;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import lombok.AllArgsConstructor;
import lombok.Getter;

@EachProperty("kestra.plugins.repositories")
@Getter
@AllArgsConstructor
public class RepositoryConfig {
    String id;

    String type = "default";

    String url;

    public RepositoryConfig(@Parameter String id) {
        this.id = id;
    }
}
