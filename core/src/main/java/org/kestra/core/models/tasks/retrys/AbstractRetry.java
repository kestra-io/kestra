package org.kestra.core.models.tasks.retrys;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

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
    @NotNull
    protected String type;

    private Duration maxDuration;

    @Min(1)
    private Integer maxAttempt;

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
}
