package io.kestra.core.models.property;

import io.micronaut.context.annotation.Context;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;

/**
 * Jakarta Bean Validation value extractor for a Property.<br>
 *
 * This is used by the @{@link io.kestra.core.validations.factory.CustomValidatorFactoryProvider}.
 */
@Context
public class PropertyValueExtractor implements ValueExtractor<Property<@ExtractedValue ?>> {

    @Override
    public void extractValues(Property<?> originalValue, ValueReceiver receiver) {
        // this will disable validation at save time but enable it at runtime when the value would be populated
        receiver.value( null, originalValue.getValue());
    }
}
