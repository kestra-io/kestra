package org.kestra.core.docs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.kestra.core.models.annotations.OutputProperty;

import java.lang.reflect.Field;

@Getter
@EqualsAndHashCode
@ToString
public class OutputDocumentation extends AbstractChildDocumentation<OutputDocumentation> {
    @JsonIgnore
    private final OutputProperty annotation;

    public String getDescription() {
        return this.annotation == null ? null : this.annotation.description();
    }

    public String getBody() {
        return this.annotation == null ? null : String.join("\n", this.annotation.body());
    }

    public OutputDocumentation(Class<?> parent, Field field, OutputProperty annotation) {
        super(
            parent,
            field.getName(),
            DocumentationGenerator.typeName(field),
            field.getType().getEnumConstants(),
            DocumentationGenerator.getChildsOutputs(field)
        );
        this.annotation = annotation;
    }
}
