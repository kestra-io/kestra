package org.kestra.core.docs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.kestra.core.models.annotations.InputProperty;

import java.lang.reflect.Field;
import javax.validation.constraints.NotNull;

@Getter
@EqualsAndHashCode
@ToString
public class InputDocumentation extends AbstractChildDocumentation<InputDocumentation> {
    @JsonIgnore
    private final InputProperty annotation;

    private Boolean required;

    public String getDescription() {
        return this.annotation == null ? null : this.annotation.description();
    }

    public String getBody() {
        return this.annotation == null ? null : String.join("\n", this.annotation.body());
    }

    public Boolean getDynamic() {
        return this.annotation == null ? null : this.annotation.dynamic();
    }

    public InputDocumentation(Class<?> parent, Field field, InputProperty annotation) {
        super(
            parent,
            field.getName(),
            DocumentationGenerator.typeName(field),
            field.getType().getEnumConstants(),
            DocumentationGenerator.getChildsInputs(field)
        );
        this.annotation = annotation;
        this.required = field.getAnnotationsByType(NotNull.class).length > 0;
    }
}
