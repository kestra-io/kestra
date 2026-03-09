package io.kestra.core.plugins.notifications;

import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public interface ExecutionInterface {
    @Schema(
        title = "The execution id to use",
        description = "Default is the current execution, " +
            "change it to {{ trigger.executionId }} if you use this task with a Flow trigger to use the original execution."
    )
    Property<String> getExecutionId();

    @Schema(
        title = "Custom fields to be added on notification"
    )
    Property<Map<String, Object>> getCustomFields();

    @Schema(
        title = "Custom message to be added on notification"
    )
    Property<String> getCustomMessage();
}