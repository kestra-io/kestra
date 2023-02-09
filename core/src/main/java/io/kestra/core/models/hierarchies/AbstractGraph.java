package io.kestra.core.models.hierarchies;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
@Introspected
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
public abstract class AbstractGraph {
    protected String uid;
    @JsonInclude
    protected String type;

    public AbstractGraph() {
        this.uid = IdUtils.create();
        this.type = this.getClass().getName();
    }

    public String getLabel() {
        return this.getUid();
    }
}
