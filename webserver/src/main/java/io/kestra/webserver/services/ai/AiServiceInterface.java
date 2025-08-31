package io.kestra.webserver.services.ai;

import io.kestra.webserver.annotation.WebServerEnabled;
import io.kestra.webserver.models.ai.FlowGenerationPrompt;
import io.reactivex.rxjava3.core.Single;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Service for chatting with an AI model.
 */
@WebServerEnabled
public interface AiServiceInterface {
    Mono<String> generateFlow(FlowGenerationPrompt flowGenerationPrompt);
}
