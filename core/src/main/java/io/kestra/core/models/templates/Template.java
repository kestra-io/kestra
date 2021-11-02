package io.kestra.core.models.templates;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.*;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@SuperBuilder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
public class Template implements DeletedInterface {
    @NotNull
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9._-]+")
    private String id;

    @NotNull
    @Pattern(regexp="[a-z0-9._-]+")
    private String namespace;

    @Valid
    @NotEmpty
    private List<Task> tasks;

    @Valid
    private List<Task> errors;

    @NotNull
    @Builder.Default
    private final boolean deleted = false;

    @JsonIgnore
    public String uid() {
        return Template.uid(
            this.getNamespace(),
            this.getId()
        );
    }

    @JsonIgnore
    public static String uid(String namespace, String id) {
        return String.join("_", Arrays.asList(
            namespace,
            id
        ));
    }

    public Optional<ConstraintViolationException> validateUpdate(Template updated) {
        Set<ConstraintViolation<?>> violations = new HashSet<>();

        if (!updated.getId().equals(this.getId())) {
            violations.add(ManualConstraintViolation.of(
                "Illegal template id update",
                updated,
                Template.class,
                "template.id",
                updated.getId()
            ));
        }

        if (!updated.getNamespace().equals(this.getNamespace())) {
            violations.add(ManualConstraintViolation.of(
                "Illegal namespace update",
                updated,
                Template.class,
                "template.namespace",
                updated.getNamespace()
            ));
        }

        if (violations.size() > 0) {
            return Optional.of(new ConstraintViolationException(violations));
        } else {
            return Optional.empty();
        }
    }

    public Template toDeleted() {
        return new Template(
            this.id,
            this.namespace,
            this.tasks,
            this.errors,
            true
        );
    }
}
