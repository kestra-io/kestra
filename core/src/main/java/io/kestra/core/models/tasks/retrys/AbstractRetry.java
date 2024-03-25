package io.kestra.core.models.tasks.retrys;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.time.Instant;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Constant.class, name = "constant"),
    @JsonSubTypes.Type(value = Exponential.class, name = "exponential"),
    @JsonSubTypes.Type(value = Random.class, name = "random")
})
@Getter
@NoArgsConstructor
@Introspected
@SuperBuilder
public abstract class AbstractRetry {
    abstract public String getType();

    private Duration maxDuration;

    @Min(1)
    private Integer maxAttempt;

    @Builder.Default
    private Boolean warningOnRetry = false;

    @Builder.Default
    private Behavior behavior = Behavior.RETRY_FAILED;

    public abstract Instant nextRetryDate(Integer attemptCount, Instant lastAttempt);

    public <T> RetryPolicy<T> toPolicy() {
        RetryPolicy<T> policy = new RetryPolicy<>();

        if (this.maxDuration != null) {
            policy.withMaxDuration(maxDuration);
        }

        if (this.maxAttempt != null) {
            policy.withMaxAttempts(this.maxAttempt);
        }

        return policy;
    }

    public static <T> RetryPolicy<T> retryPolicy(AbstractRetry retry) {
        if (retry != null) {
            return retry.toPolicy();
        }

        return new RetryPolicy<T>()
            .withMaxAttempts(1);
    }

    public enum Behavior {
        RETRY_FAILED,
        NEW_EXECUTION
    }
}
