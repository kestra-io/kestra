package io.kestra.core.test.flow;

import io.kestra.core.models.property.Property;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class Assertion {
    @NotNull
    private Property<Object> value;

    private String taskId;

    private Property<String> errorMessage;

    private Property<String> description;

    private Property<String> endsWith;
    private Property<String> startsWith;
    private Property<String> contains;
    private Property<String> equalsTo;
    private Property<String> notEqualsTo;
    private Property<Double> greaterThan;
    private Property<Double> greaterThanOrEqualTo;
    private Property<Double> lessThan;
    private Property<Double> lesThanOrEqualTo;
    private Property<List<String>> in;
    private Property<List<String>> notIn;
    private Property<Boolean> isNull;
    private Property<Boolean> isNotNull;
}
