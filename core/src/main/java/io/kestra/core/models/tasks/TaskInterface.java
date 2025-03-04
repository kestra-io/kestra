package io.kestra.core.models.tasks;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.Plugin;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public interface TaskInterface extends Plugin {
    @NotNull
    @NotBlank
    @Pattern(regexp="^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    String getId();

    @NotNull
    @NotBlank
    @Pattern(regexp="\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*")
    @Schema(title = "The class name of this task.")
    String getType();

    @Pattern(regexp="\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9]+)?|([a-zA-Z0-9]+)")
    @Schema(title = "The class version of this task.")
    String getVersion();
}
