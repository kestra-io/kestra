package io.kestra.core.validations.extractors;

import java.util.List;

import io.kestra.core.models.property.Property;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class DynamicPropertyDto {

    @NotNull
    private Property<@Min(10) Integer> number;

    @NotNull
    private Property<String> string;

    private Property<List<@Valid NestedDto>> list;

    public DynamicPropertyDto(Property<@Min(value = 10, message = "must be greater than or equal to {value}") Integer> number, Property<String> string) {
        this.number = number;
        this.string = string;
    }

    public DynamicPropertyDto(Property<Integer> number, Property<String> string, Property<List<NestedDto>> list) {
        this.number = number;
        this.string = string;
        this.list = list;
    }
}