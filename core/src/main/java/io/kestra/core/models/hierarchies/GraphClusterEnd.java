package io.kestra.core.models.hierarchies;

import io.kestra.core.utils.IdUtils;
import lombok.Getter;


@Getter
public class GraphClusterEnd extends AbstractGraph {
    public GraphClusterEnd() {
        super(IdUtils.create() + "_end");
    }

    public GraphClusterEnd(String uid) {
        super(uid + "_end");
    }
}
