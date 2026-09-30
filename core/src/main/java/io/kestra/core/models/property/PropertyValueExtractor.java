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
        Object value = originalValue.getValue();

        // Validate a property that has a default value.
        if (value != null) {
            receiver.value(null, value);
        }
    }
}
