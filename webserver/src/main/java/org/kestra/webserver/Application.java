package org.kestra.webserver;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(
    info = @Info(
        title = "kestra",
        version = "0.1",
        description = "kestra Api"
    )
)
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class);
    }
}