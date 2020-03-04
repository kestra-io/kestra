package org.kestra.core.models.hierarchies;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ParentTaskTree {
    private String id;

    private String value;
}
