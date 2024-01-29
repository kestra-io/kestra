package io.kestra.core.models.triggers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.tasks.WorkerGroup;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.slf4j.event.Level;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@SuperBuilder
@Getter
@NoArgsConstructor
@Introspected
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
abstract public class AbstractTrigger {
    @NotNull
    @NotBlank
    @Pattern(regexp="^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    @Schema(title = "A unique ID for the whole flow.")
    protected String id;

    @NotNull
    @NotBlank
    @Pattern(regexp="\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*")
    @Schema(title = "The class name for this current trigger.")
    protected String type;

    private String description;

    @Valid
    @PluginProperty
    @Schema(
        title = "List of conditions in order to limit the flow trigger."
    )
    private List<Condition> conditions;

    @NotNull
    @Builder.Default
    private boolean disabled = false;

    @Valid
    private WorkerGroup workerGroup;

    private Level minLogLevel;
}
