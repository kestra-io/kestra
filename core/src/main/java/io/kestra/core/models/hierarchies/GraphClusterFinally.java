package io.kestra.core.models.hierarchies;

import io.kestra.core.utils.IdUtils;
import lombok.Getter;

@Getter
public class GraphClusterFinally extends AbstractGraph {
    public GraphClusterFinally() {
        super("finally-" + IdUtils.create());
    }
}
