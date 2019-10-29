package org.floworc.webserver;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(
    info = @Info(
        title = "FlowOrc",
        version = "0.1",
        description = "FlowOrc Api"
    )
)
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class);
    }
}