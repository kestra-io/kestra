package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.kestra.core.models.HasUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY, defaultImpl = WorkerTaskRunning.class)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = WorkerTaskRunning.class, name = "task"),
        @JsonSubTypes.Type(value = WorkerTriggerRunning.class, name = "trigger")
    }
)
@Data
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class WorkerJobRunning implements HasUID {
    @NotNull
    private WorkerInstance workerInstance;

    abstract public String getType();

    /**
     * Whether this entry was written by a worker of a previous major version and carries none of the
     * data this version needs to act on it.
     * <p>
     * The stored shape changed in 2.0 (both subtypes moved their payload into a {@code data} field),
     * and {@link JsonIgnoreProperties} makes such an entry deserialize silently with that field left
     * {@code null}. It can neither be resubmitted nor released, and the worker that wrote it is gone
     * by definition, so it is a stale lease to be discarded rather than processed.
     *
     * @return {@code true} if this entry cannot be processed by this version.
     */
    @JsonIgnore
    abstract public boolean isLegacy();

}
