package io.kestra.core.validations.extractors;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.property.Property;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.validation.validator.Validator;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class PropertyValueExtractorTest {

    @Inject
    private Validator validator;

    @Test
    public void should_extract_and_validate_integer_value() {
        DynamicPropertyDto dto = new DynamicPropertyDto(Property.ofValue(20), Property.ofValue("Test"));
        Set<ConstraintViolation<DynamicPropertyDto>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());

        dto = new DynamicPropertyDto(Property.ofValue(5), Property.ofValue("Test"));
        violations = validator.validate(dto);
        assertThat(violations.size()).isEqualTo(1);
        ConstraintViolation<DynamicPropertyDto> violation = violations.stream().findFirst().get();
        assertThat(violation.getMessage()).isEqualTo("must be greater than or equal to 10");
    }

    // @Valid only cascades into a Property's list elements when it is on the type argument
    // (Property<List<@Valid X>>), never when it is on the field.
    @Test
    public void should_cascade_valid_into_property_list_elements() {
        DynamicPropertyDto blank = new DynamicPropertyDto(
            Property.ofValue(20), Property.ofValue("Test"), Property.ofValue(List.of(new NestedDto(" ")))
        );
        Set<ConstraintViolation<DynamicPropertyDto>> violations = validator.validate(blank);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().findFirst().get().getPropertyPath().toString()).contains("list").contains("id");

        DynamicPropertyDto valid = new DynamicPropertyDto(
            Property.ofValue(20), Property.ofValue("Test"), Property.ofValue(List.of(new NestedDto("ok")))
        );
        assertThat(validator.validate(valid)).isEmpty();
    }

    @Test
    public void should_not_cascade_valid_into_an_unrendered_expression() {
        DynamicPropertyDto dto = new DynamicPropertyDto(
            Property.ofValue(20), Property.ofValue("Test"), Property.ofExpression("{{ x }}")
        );

        assertThat(validator.validate(dto)).isEmpty();
    }

}
