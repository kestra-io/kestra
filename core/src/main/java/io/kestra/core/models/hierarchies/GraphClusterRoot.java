package io.kestra.core.models.hierarchies;

import lombok.Getter;


@Getter
public class GraphClusterRoot extends AbstractGraph {
    public GraphClusterRoot() {
        super();
    }

    public String getUid() {
        String uid = super.getUid();

        return (!uid.equals("") ? uid + "_" : "") + this.uid + "_root";
    }
}
