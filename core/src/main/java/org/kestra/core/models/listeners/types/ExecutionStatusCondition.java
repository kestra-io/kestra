package org.kestra.core.models.listeners.types;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Keep to avoid BC
 */
@Deprecated
@Schema(
    title = "Deprecated, use `org.kestra.core.models.conditions.types.ExecutionStatusCondition`"
)
public class ExecutionStatusCondition extends org.kestra.core.models.conditions.types.ExecutionStatusCondition {

}
