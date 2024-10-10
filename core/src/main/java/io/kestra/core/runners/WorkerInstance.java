package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kestra.core.models.HasUID;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Represents a Worker Instance.
 *
 * @param uid           The service ID of the worker.
 * @param workerUuid    The service ID of the worker.
 * @param workerGroup   The worker group.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkerInstance(
    String uid,
    @Deprecated
    String workerUuid,
    String workerGroup) implements HasUID {

    public WorkerInstance(String uid) {
        this(uid, null);
    }

    public WorkerInstance(String uid, String workerGroup) {
        this(uid, null, workerGroup);
    }

    @Override
    public String uid() {
        return Optional.ofNullable(uid).orElse(workerUuid);
    }

    @Override
    public String workerUuid() {
        return uid();
    }
}
