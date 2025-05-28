package io.kestra.core.validations.extractors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kestra.core.models.property.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.validation.validator.Validator;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;

import java.util.Set;
import org.junit.jupiter.api.Test;

@MicronautTest
public class PropertyValueExtractorTest {

    @Inject
    private Validator validator;

    @Test
    public void should_extract_and_validate_integer_value(){
        DynamicPropertyDto dto = new DynamicPropertyDto(Property.ofValue(20), Property.ofValue("Test"));
        Set<ConstraintViolation<DynamicPropertyDto>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());

        dto = new DynamicPropertyDto(Property.ofValue(5), Property.ofValue("Test"));
        violations = validator.validate(dto);
        assertThat(violations.size()).isEqualTo(1);
        ConstraintViolation<DynamicPropertyDto> violation = violations.stream().findFirst().get();
        assertThat(violation.getMessage()).isEqualTo("must be greater than or equal to 10");
    }

}
