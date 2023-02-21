package io.kestra.core.models.templates;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.core.annotation.Introspected;
import lombok.*;
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
@ToString
@EqualsAndHashCode
public class Template implements DeletedInterface {
    private static final ObjectMapper YAML_MAPPER = JacksonMapper.ofYaml().copy()
        .setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
            @Override
            public boolean hasIgnoreMarker(final AnnotatedMember m) {
                List<String> exclusions = Arrays.asList("revision", "deleted", "source");
                return exclusions.contains(m.getName()) || super.hasIgnoreMarker(m);
            }
        })
        .setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);

    @NotNull
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9._-]+")
    private String id;

    @NotNull
    @Pattern(regexp="[a-z0-9._-]+")
    private String namespace;

    String description;

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

    public String generateSource() {
        try {
            return YAML_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Template toDeleted() {
        return new Template(
            this.id,
            this.namespace,
            this.description,
            this.tasks,
            this.errors,
            true
        );
    }
}
