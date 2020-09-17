package org.kestra.core.models.templates;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Getter;
import org.kestra.core.models.DeletedInterface;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.validations.ManualConstraintViolation;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Builder
@Introspected
@Getter
public class Template implements DeletedInterface {
    @NotNull
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]+")
    private String id;

    @NotNull
    @Pattern(regexp="[a-z0-9.]+")
    private String namespace;

    @Valid
    @NotEmpty
    private List<Task> tasks;

    @Valid
    private List<Task> errors;

    @Builder.Default
    @NotNull
    private boolean deleted = false;

    public Optional<ConstraintViolationException> validateUpdate(Template updated) {
        Set<ConstraintViolation<?>> violations = new HashSet<>();

        if (!updated.getId().equals(this.getId())) {
            violations.add(ManualConstraintViolation.of(
                "Illegal flow id update",
                updated,
                Template.class,
                "flow.id",
                updated.getId()
            ));
        }

        if (!updated.getNamespace().equals(this.getNamespace())) {
            violations.add(ManualConstraintViolation.of(
                "Illegal namespace update",
                updated,
                Template.class,
                "flow.namespace",
                updated.getNamespace()
            ));
        }

        if (violations.size() > 0) {
            return Optional.of(new ConstraintViolationException(violations));
        } else {
            return Optional.empty();
        }
    }

}
