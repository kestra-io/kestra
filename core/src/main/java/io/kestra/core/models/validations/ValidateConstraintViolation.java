package io.kestra.core.models.validations;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

    private boolean outdated;
    private List<String> deprecationPaths;
    private List<String> warnings;
    private List<String> infos;
    private List<ValidationError> errors;

    public List<ValidationError> getErrors() {
        return errors == null ? List.of() : errors;
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
