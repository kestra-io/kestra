package org.kestra.core.models.listeners.types;

import org.kestra.core.models.annotations.Documentation;

/**
 * Keep to avoid BC
 */
@Deprecated
@Documentation(
    description = "Deprecated, use `org.kestra.core.models.conditions.types.ExecutionStatusCondition`"
)
public class ExecutionStatusCondition extends org.kestra.core.models.conditions.types.ExecutionStatusCondition {

}
