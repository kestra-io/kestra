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
    private PostHog postHog;
    private boolean initAttempted = false;

    private final HttpClient httpClient;
    private final InstanceService instanceService;
    private final VersionProvider versionProvider;
    private final EditionProvider editionProvider;

    public PosthogService(InstanceService instanceService, VersionProvider versionProvider, EditionProvider editionProvider, @Client("api") HttpClient httpClient) {
        this.instanceService = instanceService;
        this.versionProvider = versionProvider;
        this.editionProvider = editionProvider;
        this.httpClient = httpClient;
    }

    private synchronized PostHog getOrInitPostHog() {
        if (!initAttempted) {
            initAttempted = true;
            try {
                ApiConfig apiConfig = httpClient.toBlocking().retrieve("/v1/config", ApiConfig.class);
                postHog = new PostHog.Builder(apiConfig.posthog().token())
                    .host(apiConfig.posthog().apiHost())
                    .logger(new DefaultPostHogLogger())
                    .build();
            } catch (Exception e) {
                log.warn("Failed to initialize PostHog analytics (api.kestra.io may be unreachable), analytics will be disabled.", e);
            }
        }
        return postHog;
    }

    public void capture(String distinctId, String event, Map<String, Object> properties) {
        PostHog client = getOrInitPostHog();
        if (client == null) {
            return;
        }

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

        client.capture(distinctId, event, properties);
    }

    /** Gracefully shuts down the PostHog client, flushing any pending events. */
    @PreDestroy
    public void shutdown() {
        if (postHog == null) {
            return;
        }
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
