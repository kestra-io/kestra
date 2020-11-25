package org.kestra.core.models.tasks;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import javax.validation.ConstraintViolation;

public interface TaskValidationInterface <T> {
    @JsonIgnore
    List<ConstraintViolation<T>> failedConstraints();
}
