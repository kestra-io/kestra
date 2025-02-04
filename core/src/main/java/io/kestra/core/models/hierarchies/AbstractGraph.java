package io.kestra.core.models.hierarchies;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
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
    protected BranchType branchType;

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

    public void updateWithChildren(BranchType branchType) {
        this.branchType = branchType;
    }

    public AbstractGraph forExecution() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractGraph)) return false;
        return o.hashCode() == this.hashCode();
    }

    public enum BranchType {
        ERROR,
        FINALLY
    }
}
