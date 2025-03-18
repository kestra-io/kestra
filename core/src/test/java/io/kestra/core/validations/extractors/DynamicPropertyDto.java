package io.kestra.core.validations.extractors;

import io.kestra.core.models.property.Property;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class DynamicPropertyDto {

    @NotNull
    private Property<@Min(10) Integer> number;

    @NotNull
    private Property<String> string;

    public DynamicPropertyDto(Property<@Min(value = 10, message = "must be greater than or equal to {value}") Integer> number, Property<String> string) {
        this.number = number;
        this.string = string;
    }
}