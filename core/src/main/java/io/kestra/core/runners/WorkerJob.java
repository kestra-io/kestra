package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.models.HasUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY, defaultImpl = WorkerTask.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = WorkerTask.class, name = "task"),
    @JsonSubTypes.Type(value = WorkerTrigger.class, name = "trigger")
})
public abstract class WorkerJob implements HasUID {
    abstract public String getType();

}
