package io.kestra.core.validations.extractors;

import io.kestra.core.models.property.Property;

import jakarta.validation.constraints.Size;

/**
 * A string constraint that the raw text of a Pebble expression would break, to tell an expression
 * from a literal.
 */
public class ShortCodePropertyDto {

    private Property<@Size(max = 3) String> code;

    public ShortCodePropertyDto(Property<String> code) {
        this.code = code;
    }
}
