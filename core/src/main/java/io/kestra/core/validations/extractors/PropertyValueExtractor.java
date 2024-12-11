package io.kestra.core.validations.extractors;

import io.kestra.core.models.property.Property;
import io.micronaut.context.annotation.Context;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;

@Context
public class PropertyValueExtractor implements ValueExtractor<Property<@ExtractedValue ?>> {

    @Override
    public void extractValues(Property<?> originalValue, ValueReceiver receiver) {
        receiver.value( null, originalValue.getValue());
    }
}
