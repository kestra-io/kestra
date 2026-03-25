package io.kestra.webserver.services.posthog;

import java.util.HashMap;
import java.util.Map;

import com.posthog.java.DefaultPostHogLogger;
import com.posthog.java.PostHog;

import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.EditionProvider;
import io.kestra.core.utils.VersionProvider;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PosthogService {
    private final PostHog postHog;

    private final InstanceService instanceService;
    private final VersionProvider versionProvider;
    private final EditionProvider editionProvider;

    public PosthogService(InstanceService instanceService, VersionProvider versionProvider, EditionProvider editionProvider, @Client("api") HttpClient httpClient) {
        this.instanceService = instanceService;
        this.versionProvider = versionProvider;
        this.editionProvider = editionProvider;

        ApiConfig apiConfig = httpClient.toBlocking().retrieve("/v1/config", ApiConfig.class);

        this.postHog = new PostHog.Builder(apiConfig.posthog().token())
            .host(apiConfig.posthog().apiHost())
            .logger(new DefaultPostHogLogger())
            .build();
    }

    public void capture(String distinctId, String event, Map<String, Object> properties) {
        properties = new HashMap<>(properties);
        properties.putAll(
            Map.of(
                "from", "APP",
                "iid", instanceService.fetch(),
                "app", Map.of(
                    "version", versionProvider.getVersion(),
                    "type", editionProvider.get()
                )
            )
        );

        postHog.capture(distinctId, event, properties);
    }

    /** Gracefully shuts down the PostHog client, flushing any pending events. */
    @PreDestroy
    public void shutdown() {
        try {
            postHog.shutdown();
        } catch (Exception e) {
            log.warn("Error shutting down PostHog client", e);
        }
    }

    private record PosthogConfig(String apiHost, String token) {
    }

    private record ApiConfig(PosthogConfig posthog) {
    }
}
