package io.kestra.core.models.tasks.retrys;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.failsafe.RetryPolicy;
import dev.failsafe.RetryPolicyBuilder;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
@SuperBuilder
@Introspected
public abstract class AbstractRetry {
    abstract public String getType();

    private Duration maxDuration;

    @Deprecated(forRemoval = true)
    public Integer getMaxAttempt() {
        return maxAttempts;
    }

    @Deprecated(forRemoval = true)
    public void setMaxAttempt(@Min(1) Integer maxAttempt) {
        this.maxAttempts = maxAttempt;
    }

    @Min(1)
    private Integer maxAttempts;

    @Builder.Default
    private Boolean warningOnRetry = false;

    @Builder.Default
    private Behavior behavior = Behavior.RETRY_FAILED_TASK;

    public abstract Instant nextRetryDate(Integer attemptCount, Instant lastAttempt);

    public <T> RetryPolicyBuilder<T> toPolicy() {
        RetryPolicyBuilder<T> builder = RetryPolicy.builder();
        if (this.maxDuration != null) {
            builder.withMaxDuration(maxDuration);
        }

        if (this.maxAttempts != null) {
            builder.withMaxAttempts(this.maxAttempts);
        }
        return builder;
    }

    public static <T> RetryPolicyBuilder<T> retryPolicy(AbstractRetry retry) {
        if (retry != null) {
            return retry.toPolicy();
        }
        return RetryPolicy.<T>builder().withMaxAttempts(1);
    }

    public enum Behavior {
        RETRY_FAILED_TASK,
        CREATE_NEW_EXECUTION
    }
}
