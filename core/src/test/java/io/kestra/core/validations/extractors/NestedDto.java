package io.kestra.core.validations.extractors;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class NestedDto {

    @NotBlank
    private String id;

    public NestedDto(String id) {
        this.id = id;
    }
}
