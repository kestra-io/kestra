package org.kestra.webserver;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;

@OpenAPIDefinition(
    info = @Info(
        title = "Kestra",
        license = @License(name = "Apache 2.0", url = "https://raw.githubusercontent.com/kestra-io/kestra/master/LICENSE")
    )
)
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class);
    }
}