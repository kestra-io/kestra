package io.kestra.webserver.services;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.utils.NamespaceUtils;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.annotation.WebServerEnabled;
import io.kestra.webserver.controllers.api.BlueprintController.ApiBlueprintItem;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.function.Function;

/**
 * Service for automatically loading initial flows from the community blueprints at server startup.
 */
@Singleton
@WebServerEnabled
@Requires(property = "kestra.tutorial-flows.enabled", value = "true", defaultValue = "true")
public class FlowAutoLoaderService {
    private static final Logger log = LoggerFactory.getLogger(FlowAutoLoaderService.class);

    public static final String PURGE_SYSTEM_FLOW_BLUEPRINT_ID = "234";

    @Inject
    protected FlowRepositoryInterface repository;

    @Inject
    protected PluginDefaultService pluginDefaultService;

    @Inject
    @Client("api")
    protected HttpClient httpClient;

    @Inject
    private YamlParser yamlParser;

    @Inject
    private NamespaceUtils namespaceUtils;

    @Inject
    private VersionProvider versionProvider;

    @SuppressWarnings("unchecked")
    public void load() {
        try {
            // Loads all flows.
            Integer count = Mono.from(httpClient
                    .exchange(
                        HttpRequest.create(HttpMethod.GET, "/v1/blueprints/kinds/flow/versions/" + versionProvider.getVersion() + "?tags=getting-started"),
                        Argument.of(PagedResults.class, ApiBlueprintItem.class)
                    ))
                .map(response -> ((PagedResults<ApiBlueprintItem>)response.body()).getResults())
                .flatMapIterable(Function.identity())
                .flatMap(it -> Mono.from(httpClient
                    .exchange(
                        HttpRequest.create(HttpMethod.GET, "/v1/blueprints/kinds/flow/" + it.getId() + "/versions/" + versionProvider.getVersion() + "/source"),
                        Argument.STRING
                    )).mapNotNull(response -> {
                        String body = response.body();
                        if (it.getId().equals(PURGE_SYSTEM_FLOW_BLUEPRINT_ID)) {
                            return NamespaceUtils.NAMESPACE_FROM_FLOW_SOURCE_PATTERN.matcher(Objects.requireNonNull(body)).replaceFirst("namespace: " + namespaceUtils.getSystemFlowNamespace());
                        }
                        return body;
                    })
                )
                .map(source -> {
                    Flow flow = yamlParser.parse(source, Flow.class);
                    repository.create(flow, source, pluginDefaultService.injectDefaults(flow.withSource(source)));
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
                "You can disable this feature by setting 'kestra.tutorialFlows.enabled=false'.", count);
        } catch (Exception e) {
            // Kestra's API is likely to be unavailable.
            log.warn("Unable to load \"Getting Started\" flows from community blueprints. " +
                "You can disable this feature by setting 'kestra.tutorialFlows.enabled=false'. Cause: {}", e.getMessage());
        }
    }
}
