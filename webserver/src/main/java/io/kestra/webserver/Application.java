package io.kestra.webserver;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.tags.Tag;

@OpenAPIDefinition(
    info = @Info(
        title = "Kestra",
        license = @License(name = "Apache 2.0", url = "https://raw.githubusercontent.com/kestra-io/kestra/master/LICENSE")
    ),
    tags = {
        @Tag(name = "Flows", description = "Flows api"),
        @Tag(name = "Templates", description = "Templates api"),
        @Tag(name = "Executions", description = "Executions api"),
        @Tag(name = "Logs", description = "Logs api"),
        @Tag(name = "Plugins", description = "Plugins api"),
        @Tag(name = "Stats", description = "Stats api"),
        @Tag(name = "Misc", description = "Misc api"),
    }
)
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class);
    }
}