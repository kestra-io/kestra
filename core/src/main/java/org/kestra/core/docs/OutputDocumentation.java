package org.kestra.core.docs;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.kestra.core.models.annotations.OutputProperty;

import java.lang.reflect.Field;

@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class OutputDocumentation extends AbstractChildDocumentation<OutputDocumentation> {
    private String description;
    private String body;

    public OutputDocumentation(Class<?> parent, Field field, OutputProperty annotation) {
        super(
            parent,
            field.getName(),
            DocumentationGenerator.typeName(field),
            field.getType().getEnumConstants(),
            DocumentationGenerator.getChildsOutputs(field)
        );

        this.description = annotation == null ? null : annotation.description();
        this.body = annotation == null ? null : String.join("\n", annotation.body());
    }
}
