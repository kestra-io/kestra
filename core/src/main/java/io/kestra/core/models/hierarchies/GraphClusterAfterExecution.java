package io.kestra.core.models.hierarchies;

import io.kestra.core.utils.IdUtils;
import lombok.Getter;

@Getter
public class GraphClusterAfterExecution extends AbstractGraph {
    public GraphClusterAfterExecution() {
        super("after-execution-" + IdUtils.create());
    }
}
