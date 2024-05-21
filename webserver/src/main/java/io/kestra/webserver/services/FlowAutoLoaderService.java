package io.kestra.webserver.services;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.webserver.annotation.WebServerEnabled;
import io.kestra.webserver.controllers.api.BlueprintController.BlueprintItem;
import io.kestra.webserver.controllers.api.BlueprintController.BlueprintTagItem;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

/**
 * Service for automatically loading initial flows from the community blueprints at server startup.
 */
@Singleton
@WebServerEnabled
@Requires(property = "kestra.tutorial-flows.enabled", value = "true", defaultValue = "true")
public class FlowAutoLoaderService {

    private static final Logger log = LoggerFactory.getLogger(FlowAutoLoaderService.class);

    @Inject
    protected FlowRepositoryInterface repository;

    @Inject
    protected TaskDefaultService taskDefaultService;

    @Inject
    @Client("api")
    protected HttpClient httpClient;

    @Inject
    private YamlFlowParser yamlFlowParser;

    @SuppressWarnings("unchecked")
    public void load() {
        // Gets the tag ID for 'Getting Started'.
        try {
            Optional<String> optionalTagId = Mono.from(httpClient
                    .exchange(
                        HttpRequest.create(HttpMethod.GET, "/v1/blueprints/tags"),
                        Argument.listOf(BlueprintTagItem.class)
                    )
                )
                .blockOptional()
                .stream()
                .map(HttpResponse::body)
                .flatMap(Collection::stream)
                .filter(it -> it.getName().equalsIgnoreCase("Getting Started"))
                .map(BlueprintTagItem::getId)
                .findFirst();

            if (optionalTagId.isEmpty()) {
                return;
            }

            // Loads all flows.
            Integer count = Mono.from(httpClient
                    .exchange(
                        HttpRequest.create(HttpMethod.GET, "/v1/blueprints/?tags=" + optionalTagId.get()),
                        Argument.of(PagedResults.class, BlueprintItem.class)
                    ))
                .map(response -> ((PagedResults<BlueprintItem>)response.body()).getResults())
                .flatMapIterable(Function.identity())
                .flatMap(it -> httpClient
                    .exchange(
                        HttpRequest.create(HttpMethod.GET, "/v1/blueprints/" + it.getId() + "/flow"),
                        Argument.STRING
                    )
                )
                .map(HttpResponse::body)
                .map(source -> {
                    Flow flow = yamlFlowParser.parse(source, Flow.class);
                    repository.create(flow, source, taskDefaultService.injectDefaults(flow));
                    log.debug("Loaded flow '{}/{}'.", flow.getNamespace(), flow.getId());
                    return 1;
                })
                .onErrorReturn(0)
                .onErrorContinue((throwable, o) -> {
                    // log error in debug to not spam user with stacktrace, e.g., flow maybe already registered.
                    log.debug("Failed to load a flow from community blueprints. Error: {}\n{}", throwable.getMessage(), o);
                })
                .reduce(Integer::sum)
                .blockOptional()
                .orElse(0);
            log.info(
                "Loaded {} \"Getting Started\" flows from community blueprints. " +
                "You can disable this feature by setting 'kestra.tutorial-flows.enabled=false'.", count);
        } catch (Exception e) {
            // Kestra's API is likely to be unavailable.
            log.warn("Unable to load \"Getting Started\" flows from community blueprints. " +
                "You can disable this feature by setting 'kestra.tutorial-flows.enabled=false'. Cause: {}", e.getMessage());
        }
    }
}
