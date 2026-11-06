package io.kestra.core.validations.extractors;

import io.kestra.core.models.property.Property;

import jakarta.validation.constraints.NotBlank;

/**
 * Carries the one shape {@link DynamicPropertyDto} cannot: a constraint that rejects null sitting
 * on the type argument, which is what a plugin writes for a required string property.
 */
public class NotBlankPropertyDto {

    private Property<@NotBlank String> format;

    public NotBlankPropertyDto(Property<String> format) {
        this.format = format;
    }
}
