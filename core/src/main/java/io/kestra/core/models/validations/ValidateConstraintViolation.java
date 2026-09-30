package io.kestra.core.models.validations;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@SuperBuilder(toBuilder = true)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Slf4j
@EqualsAndHashCode
public class ValidateConstraintViolation {
    @NotNull
    private int index;
    private String filename;

    private String namespace;
    private String flow;

    @Schema(deprecated = true, description = "Every error message joined by a newline. Use `errors` instead.")
    private String constraints;
    private boolean outdated;
    private List<String> deprecationPaths;
    private List<String> warnings;
    private List<String> infos;
    @Schema(description = "One entry per error, present whenever `constraints` is.")
    private List<ValidationError> errors;

    /** Producers that only set {@code constraints} still expose one error per line of it. */
    public List<ValidationError> getErrors() {
        if (errors != null && !errors.isEmpty()) {
            return errors;
        }
        if (constraints == null) {
            return List.of();
        }
        return constraints.lines().map(String::strip).filter(line -> !line.isEmpty()).map(ValidationError::of).toList();
    }

    @JsonIgnore
    public String getIdentity() {
        return (namespace != null && flow != null) ? getFlowId() : (flow != null) ? flow : (filename != null) ? filename : String.valueOf(index);
    }

    @JsonIgnore
    public String getFlowId() {
        return namespace + "." + flow;
    }
}
