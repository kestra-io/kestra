package org.kestra.core.docs;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.kestra.core.models.annotations.InputProperty;

import java.lang.reflect.Field;
import javax.validation.constraints.NotNull;

@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class InputDocumentation extends AbstractChildDocumentation<InputDocumentation> {
    private String description;
    private String body;
    private Boolean dynamic;
    private Boolean required;

    public InputDocumentation(Class<?> parent, Field field, InputProperty annotation) {
        super(
            parent,
            field.getName(),
            DocumentationGenerator.typeName(field),
            field.getType().getEnumConstants(),
            DocumentationGenerator.getChildsInputs(field)
        );
        this.description = annotation == null ? null : annotation.description();
        this.body = annotation == null ? null : String.join("\n", annotation.body());
        this.dynamic = annotation == null ? null : annotation.dynamic();
        this.required = field.getAnnotationsByType(NotNull.class).length > 0;
    }
}
