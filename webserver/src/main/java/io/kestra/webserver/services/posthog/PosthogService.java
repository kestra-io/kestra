package io.kestra.webserver.services.posthog;

import com.posthog.java.DefaultPostHogLogger;
import com.posthog.java.PostHog;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.EditionProvider;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class PosthogService {
    private PostHog postHog;

    private InstanceService instanceService;
    private VersionProvider versionProvider;
    private EditionProvider editionProvider;

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
        properties.putAll(Map.of(
            "from", "APP",
            "iid", instanceService.fetch(),
            "app", Map.of(
                "version", versionProvider.getVersion(),
                "type", editionProvider.get()
            )));

        postHog.capture(distinctId, event, properties);
    }

    private record PosthogConfig(String apiHost, String token) {
    }

    private record ApiConfig(PosthogConfig posthog) {
    }
}
