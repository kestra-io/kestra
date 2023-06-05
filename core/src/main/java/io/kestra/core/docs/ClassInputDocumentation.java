package io.kestra.core.docs;

import io.kestra.core.models.flows.Input;
import lombok.*;

@SuppressWarnings("rawtypes")
@Getter
@EqualsAndHashCode
@ToString
public class ClassInputDocumentation extends AbstractClassDocumentation<Input> {
    public ClassInputDocumentation(JsonSchemaGenerator jsonSchemaGenerator, Class<? extends Input> cls) {
        super(jsonSchemaGenerator, cls, Input.class);
    }

    public static ClassInputDocumentation of(JsonSchemaGenerator jsonSchemaGenerator, Class<? extends Input> cls) {
        return new ClassInputDocumentation(
            jsonSchemaGenerator,
            cls
        );
    }
}
