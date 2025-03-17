package io.kestra.core.validations.extractors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kestra.core.models.property.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.validation.validator.Validator;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

@MicronautTest
public class PropertyValueExtractorTest {

    @Inject
    private Validator validator;

    @Test
    public void should_extract_and_validate_integer_value(){
        DynamicPropertyDto dto = new DynamicPropertyDto(Property.of(20), Property.of("Test"));
        Set<ConstraintViolation<DynamicPropertyDto>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());

        dto = new DynamicPropertyDto(Property.of(5), Property.of("Test"));
        violations = validator.validate(dto);
        assertThat(violations.size(), is(1));
        ConstraintViolation<DynamicPropertyDto> violation = violations.stream().findFirst().get();
        assertThat(violation.getMessage(), is("must be greater than or equal to 10"));
    }

}
