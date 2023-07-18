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
        @Tag(name = "Flows", description = "Flows API"),
        @Tag(name = "Templates", description = "Templates API"),
        @Tag(name = "Executions", description = "Executions API"),
        @Tag(name = "Logs", description = "Logs API"),
        @Tag(name = "Plugins", description = "Plugins API"),
        @Tag(name = "Stats", description = "Stats API"),
        @Tag(name = "Misc", description = "Misc API"),
        @Tag(name = "Blueprints", description = "Blueprints API"),
        @Tag(name = "Blueprint Tags", description = "Blueprint Tags API"),
        @Tag(name = "Metrics", description = "Metrics API"),
    }
)
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class);
    }
}