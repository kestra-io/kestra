package io.kestra.core.validations;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Data;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ModelValidator;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@KestraTest
public class DataValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void valid() throws Exception {
        Data<?> data = Data.ofURI(URI.create("kestra:///uri"));
        assertThat(modelValidator.isValid(data).isEmpty(), is(true));

        data = Data.ofMap(Map.of("key", "value"));
        assertThat(modelValidator.isValid(data).isEmpty(), is(true));

        data = Data.ofList(List.of(Map.of("key1", "value11"), Map.of("key2", "value2")));
        assertThat(modelValidator.isValid(data).isEmpty(), is(true));
    }

    @Test
    void invalid() throws Exception {
        Data<?> data = Data.builder()
            .fromURI(Property.of(URI.create("kestra:///uri")))
            .fromList(new Property<>())
            .build();

        assertThat(modelValidator.isValid(data).isEmpty(), is(false));
        assertThat(modelValidator.isValid(data).get().getMessage(), containsString("Only one of 'fromURI', 'fromMap' or 'fromList' can be set."));
    }
}
