package org.kestra.core.models.tasks.retrys;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.jodah.failsafe.RetryPolicy;
import org.kestra.core.runners.WorkerTask;

import javax.validation.constraints.NotNull;
import java.time.Duration;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Constant.class, name = "constant"),
    @JsonSubTypes.Type(value = Exponential.class, name = "exponential"),
    @JsonSubTypes.Type(value = Random.class, name = "random")
})
@Getter
@NoArgsConstructor
public abstract class AbstractRetry {
    @NotNull
    protected String type;

    private Duration maxDuration;

    private Integer maxAttempt;

    public RetryPolicy<WorkerTask> toPolicy() {
        RetryPolicy<WorkerTask> policy = new RetryPolicy<>();

        if (this.maxDuration != null) {
            policy.withMaxDuration(maxDuration);
        }

        if (this.maxAttempt != null) {
            policy.withMaxAttempts(this.maxAttempt);
        }

        return policy;
    }
}
