package io.kestra.core.models.triggers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@Builder(toBuilder = true)
@Getter
@Introspected
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class AbstractTriggerForExecution implements AbstractTriggerInterface {
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

    public static AbstractTriggerForExecution of(AbstractTrigger abstractTrigger) {
        return AbstractTriggerForExecution.builder()
            .id(abstractTrigger.getId())
            .type(abstractTrigger.getType())
            .build();
    }
}
