package io.kestra.webserver.services.posthog;

import com.posthog.java.DefaultPostHogLogger;
import com.posthog.java.PostHog;
import io.micronaut.context.annotation.Factory;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URI;

@Factory
public class PosthogFactory {
    @Inject
    @Client("api")
    private HttpClient httpClient;

    @Singleton
    public PostHog posthog() {
        ApiConfig apiConfig = httpClient.toBlocking().retrieve("/v1/config", ApiConfig.class);

        return new PostHog.Builder(apiConfig.posthog().token())
            .host(apiConfig.posthog().apiHost())
            .logger(new DefaultPostHogLogger())
            .build();
    }

    private record PosthogConfig(String apiHost, String token) {}

    private record ApiConfig(PosthogConfig posthog) {}
}
