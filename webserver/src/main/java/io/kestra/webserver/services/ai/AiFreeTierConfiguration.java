package io.kestra.webserver.services.ai;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

/**
 * The hosted Copilot provider used when an instance has configured none of its own — without it a fresh
 * install answers 503, since no provider is registered. The key, the model and the allowance all live at the
 * relay's end, so nothing set here changes what answers.
 *
 * <p>Everything on this path leaves the deployment: prompts, the flow YAML being authored, and tool results
 * that can include flow source and execution logs. Set {@code kestra.ai.free-tier.enabled: false} where that
 * is not acceptable.
 *
 * <p>A class with field defaults rather than a record with YAML ones, so a distribution can change the default
 * by extending it and replacing the bean. Explicit configuration still wins, because
 * {@code @ConfigurationProperties} only calls a setter for a property that is actually present.
 */
@ConfigurationProperties("kestra.ai.free-tier")
@Getter
@Setter
public class AiFreeTierConfiguration {
    /** The provider id the free tier registers under, and what the UI sees as the active provider. */
    public static final String PROVIDER_ID = "kestra-free-tier";

    public static final String DISPLAY_NAME = "Kestra (free tier)";

    /**
     * The model named in the request URL, which the relay ignores — it serves whatever model it funds. A value
     * is needed only because the client composes {@code {baseUrl}/models/{modelName}:...}; deliberately not a
     * real model id, so it cannot advertise a choice the caller does not have.
     */
    public static final String MODEL_NAME = "kestra-free-model";

    private boolean enabled = true;

    private String baseUrl = "https://api.kestra.io/v1/ai/relay/gemini";

    /** Generous by default: a relayed agent turn streams, and can sit quiet between chunks. */
    private Duration timeout = Duration.ofMinutes(5);
}
