package org.kestra.core.models.triggers;

import lombok.Data;

@Data
abstract public class Trigger {
    private String type;
}
