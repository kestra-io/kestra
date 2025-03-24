package io.kestra.core.models.hierarchies;

import io.kestra.core.utils.IdUtils;
import lombok.Getter;


@Getter
public class GraphClusterEnd extends AbstractGraph {
    public GraphClusterEnd() {
        super("end-" + IdUtils.create());
    }
}
