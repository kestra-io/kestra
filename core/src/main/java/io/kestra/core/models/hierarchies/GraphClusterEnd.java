package io.kestra.core.models.hierarchies;

import lombok.Getter;


@Getter
public class GraphClusterEnd extends AbstractGraph {
    public GraphClusterEnd() {
        super();
    }

    public String getUid() {
        String uid = super.getUid();

        return (!uid.equals("") ? uid + "_" : "") + this.uid + "_end";
    }
}
