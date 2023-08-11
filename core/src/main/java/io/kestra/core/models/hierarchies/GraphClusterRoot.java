package io.kestra.core.models.hierarchies;

import io.kestra.core.utils.IdUtils;
import lombok.Getter;

@Getter
public class GraphClusterRoot extends AbstractGraph {
    public GraphClusterRoot() {
        super(IdUtils.create() + "_start");
    }

    public GraphClusterRoot(String uid) {
        super(uid + "_start");
    }
}
