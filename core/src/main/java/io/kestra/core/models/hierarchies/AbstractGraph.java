package io.kestra.core.models.hierarchies;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Introspected
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
public abstract class AbstractGraph {
    @Setter
    protected String uid;
    @JsonInclude
    protected String type;
    @Setter
    protected boolean error;

    public AbstractGraph() {
        this.type = this.getClass().getName();
    }

    public AbstractGraph(String uid) {
        this.uid = uid;
        this.type = this.getClass().getName();
    }

    @JsonIgnore
    public String getLabel() {
        return this.getUid();
    }

    public void updateUidWithChildren(String uid) {
        this.uid = uid;
    }

    public void updateErrorWithChildren(boolean error) {
        this.error = error;
    }

    public AbstractGraph forExecution() {
        return this;
    }
}
