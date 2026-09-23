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

        // A property parsed from a flow carries only its expression until something renders it, so
        // there is no value to validate at save time. Reporting the absent value as null skipped
        // the constraints that accept null, but failed the ones that reject it: @NotBlank on a
        // literal the user had written was reported as blank. Reporting no element instead leaves
        // every constraint alone until the value exists, which is where
        // {@link io.kestra.core.runners.RunContextProperty} validates the task again.
        if (value != null) {
            receiver.value(null, value);
        }
    }
}
