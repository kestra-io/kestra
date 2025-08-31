package io.kestra.webserver.controllers.api;

import io.kestra.webserver.models.ai.FlowGenerationPrompt;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Validated
@Controller("/api/v1/main/ai")
@Requires(bean = AiServiceInterface.class)
public class AiController {
    @Inject
    private AiServiceInterface aiService;

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/generate/flow", produces = "application/yaml")
    @Operation(tags = {"AI"}, summary = "Generate or regenerate a flow based on a prompt")
    public Mono<String> generateFlow(
        @RequestBody(description = "Prompt and context required for flow generation") @Body FlowGenerationPrompt flowGenerationPrompt
    ) {
        return aiService.generateFlow(flowGenerationPrompt);
    }
}
