package io.kestra.webserver.services.ai;

import io.kestra.core.serializers.JacksonMapper;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * The hosted Copilot provider used when an instance has configured none of its own.
 *
 * <p>Without this, a fresh install has no Copilot at all: the endpoints answer 503 because no provider is
 * registered. With it, the instance keeps the prompts, the tools and the agent loop — only the provider key
 * and the spend ceiling live at Kestra's end, so the free tier is the real Copilot rather than a reduced one.
 *
 * <p><b>Enabled here, disabled in Enterprise.</b> Everything on this path leaves the deployment: prompts, the
 * flow YAML being authored, and tool results that can include flow source and execution logs. That is an
 * acceptable, communicable trade for an OSS install, demo or trial, where the alternative is a feature that
 * does not work. It is not acceptable by default for a deployment with data-residency commitments and its own
 * provider contract.
 *
 * <p>A class rather than a record, and defaults in fields rather than in YAML, so Enterprise can flip the
 * default by <em>extending</em> this and replacing the bean. Expressing it as a default in one YAML file and
 * an override in another depends on file precedence, which is not obvious from reading either file and is
 * easy to get wrong; a field default is unambiguous, and explicit configuration still wins because
 * {@code @ConfigurationProperties} only calls a setter for a property that is actually present.
 */
@ConfigurationProperties("kestra.ai.free-tier")
@Getter
@Setter
public class AiFreeTierConfiguration {
    /** The provider id the free tier registers under, and what the UI sees as the active provider. */
    public static final String PROVIDER_ID = "kestra-free-tier";

    public static final String DISPLAY_NAME = "Kestra (free tier)";

    private boolean enabled = true;

    private String baseUrl = "https://api.kestra.io/v1/ai/relay/gemini";

    /**
     * Sent so the client library can compose its request URL, and ignored by the relay, which serves whatever
     * model it funds. Changing it here changes nothing about what answers.
     */
    private String modelName = "gemini-3.1-flash-lite";

    /**
     * Not a secret. The relay presents its own provider credential and discards whatever a caller sends; this
     * exists only because the client library expects the field to be populated.
     */
    private String token = "kestra-free-tier";

    /** Generous by default: a relayed agent turn streams, and can sit quiet between chunks. */
    private Duration timeout = Duration.ofMinutes(5);

    @Getter(AccessLevel.NONE)
    @Nullable
    private AiUsageLimitConfiguration usageLimit;

    /**
     * The instance's own ceiling on hosted spend, disabled by default like every other provider's.
     *
     * <p>Not the relay's quota, which lives at Kestra's end and answers 429 when exhausted — this is what the
     * instance decides to spend of it, so an operator can cap or subdivide the allowance they are given.
     */
    public Optional<AiUsageLimitConfiguration> usageLimit() {
        return Optional.ofNullable(usageLimit).filter(AiUsageLimitConfiguration::enabled);
    }

    /**
     * Taken as a map and converted here, exactly as a declared provider's own {@code usage-limit} is: Micronaut's
     * property binder cannot construct a record as a property of a configuration class — it binds silently to
     * nothing, so every value an operator wrote would be lost while appearing to be accepted.
     *
     * <p>Converting once, here, rather than per read: the ceiling is consulted on every model call, and an
     * unreadable one is worth failing the instance at startup rather than the turn that reads it.
     *
     * <p>The keys are camel-cased on the way in because configuration is written in kebab-case and the record's
     * components are not: without it {@code max-weight} would be an unknown property, and a ceiling an operator
     * wrote would read as absent.
     */
    public void setUsageLimit(
        @Nullable
        @MapFormat(transformation = MapFormat.MapTransformation.NESTED, keyFormat = StringConvention.CAMEL_CASE)
        final Map<String, Object> usageLimit) {
        if (usageLimit == null || usageLimit.isEmpty()) {
            this.usageLimit = null;
            return;
        }

        try {
            this.usageLimit = JacksonMapper.ofJson().convertValue(usageLimit, AiUsageLimitConfiguration.class);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Cannot read the AI free tier usage limit from 'kestra.ai.free-tier.usage-limit': %s.".formatted(e.getMessage()),
                e
            );
        }
    }
}
