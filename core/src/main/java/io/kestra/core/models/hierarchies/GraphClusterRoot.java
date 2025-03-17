package io.kestra.core.models.hierarchies;

import io.kestra.core.utils.IdUtils;
import lombok.Getter;

@Getter
public class GraphClusterRoot extends AbstractGraph {
    public GraphClusterRoot() {
        super("root-" + IdUtils.create());
    }
}
