package io.kestra.core.test.flow;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.kestra.core.test.flow.Assertion.Operator.*;

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
    private Property<Object> equalsTo;
    private Property<Object> notEqualsTo;
    private Property<Double> greaterThan;
    private Property<Double> greaterThanOrEqualTo;
    private Property<Double> lessThan;
    private Property<Double> lessThanOrEqualTo;
    private Property<List<String>> in;
    private Property<List<String>> notIn;
    private Property<Boolean> isNull;
    private Property<Boolean> isNotNull;

    public enum Operator{
        ENDS_WITH("endsWith"),
        STARTS_WITH("startsWith"),
        CONTAINS("contains"),
        EQUALS_TO("equalsTo"),
        NOT_EQUALS_TO("notEqualsTo"),
        GREATER_THAN("greaterThan"),
        GREATER_THAN_OR_EQUAL_TO("greaterThanOrEqualTo"),
        LESS_THAN("lessThan"),
        LESS_THAN_OR_EQUAL_TO("lessThanOrEqualTo"),
        IN("in"),
        NOT_IN("notIn"),
        IS_NULL("isNull"),
        IS_NOT_NULL("isNotNull"),;

        public final String key;
        Operator(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return key;
        }
    }

    public List<AssertionResult> run(RunContext runContext) {
        try {
            var actualValueQuery = this.getValue().toString();
            var actualValue = runContext.render(this.getValue()).as(Object.class).orElse(null);
            var errorMessage = runContext.render(this.getErrorMessage()).as(String.class);
            var description = runContext.render(this.getDescription()).as(String.class);
            var taskId = Optional.ofNullable(this.getTaskId());

            var results = new ArrayList<AssertionResult>();

            runContext.render(this.getIsNull()).as(Boolean.class)
                .ifPresent(isNullExpected ->
                    results.add(
                        getIsNullResult(IS_NULL, isNullExpected, actualValueQuery, actualValue, taskId, description, errorMessage))
                );
            runContext.render(this.getIsNotNull()).as(Boolean.class)
                .ifPresent(isNotNullExpected ->
                    results.add(
                        getIsNullResult(IS_NOT_NULL, !isNotNullExpected, actualValueQuery, actualValue, taskId, description, errorMessage))
                );
            runContext.render(this.getEndsWith()).as(String.class)
                .ifPresent(expectedValue -> results.add(
                    endsWith(expectedValue, actualValueQuery, actualValue, taskId, description, errorMessage)
                ));
            runContext.render(this.getStartsWith()).as(String.class)
                .ifPresent(expectedValue -> results.add(
                    startsWith(expectedValue, actualValueQuery, actualValue, taskId, description, errorMessage)
                ));
            runContext.render(this.getEqualsTo()).as(Object.class)
                .ifPresent(expectedValue -> results.add(
                    equalsTo(expectedValue, actualValueQuery, actualValue, taskId, description, errorMessage)
                ));
            runContext.render(this.getContains()).as(String.class)
                .ifPresent(expectedValue -> results.add(
                    contains(expectedValue, actualValueQuery, actualValue, taskId, description, errorMessage)
                ));
            runContext.render(this.getNotEqualsTo()).as(Object.class)
                .ifPresent(expectedValue -> results.add(
                    notEqualsTo(expectedValue, actualValueQuery, actualValue, taskId, description, errorMessage)
                ));
            var expectedGreaterThanValue = runContext.render(this.getGreaterThan()).as(Double.class);
            if (expectedGreaterThanValue.isPresent()) {
                results.add(
                    greaterThan(expectedGreaterThanValue.get(), actualValueQuery, actualValue, taskId, description, errorMessage)
                );
            }
            var expectedGreaterThanOrEqualToValue = runContext.render(this.getGreaterThanOrEqualTo()).as(Double.class);
            if (expectedGreaterThanOrEqualToValue.isPresent()) {
                results.add(
                    greaterThanOrEqualTo(expectedGreaterThanOrEqualToValue.get(), actualValueQuery, actualValue, taskId, description, errorMessage)
                );
            }
            var expectedLessThanValue = runContext.render(this.getLessThan()).as(Double.class);
            if (expectedLessThanValue.isPresent()) {
                results.add(
                    lessThan(expectedLessThanValue.get(), actualValueQuery, actualValue, taskId, description, errorMessage));
            }
            var expectedLessThanOrEqualToValue = runContext.render(this.getLessThanOrEqualTo()).as(Double.class);
            if (expectedLessThanOrEqualToValue.isPresent()) {
                results.add(
                    lessThanOrEqualTo(expectedLessThanOrEqualToValue.get(), actualValueQuery, actualValue, taskId, description, errorMessage)
                );
            }
            var expectedInList = runContext.render(this.getIn()).asList(String.class);
            if (!expectedInList.isEmpty()) {
                results.add(
                    in(expectedInList, actualValueQuery, actualValue, taskId, description, errorMessage)
                );
            }
            var notExpectedInList = runContext.render(this.getNotIn()).asList(String.class);
            if (!notExpectedInList.isEmpty()) {
                results.add(
                    notIn(notExpectedInList, actualValueQuery, actualValue, taskId, description, errorMessage)
                );
            }

            if (results.isEmpty()) {
                throw new IllegalArgumentException("no assertion to run found");
            }
            return results;
        } catch (IllegalVariableEvaluationException e) {
            throw new RuntimeException(e);
        }
    }

    private AssertionResult getIsNullResult(Operator operator, Boolean isNullExpected, String actualValueQuery, Object actualValue, Optional<String> taskId, Optional<String> description, Optional<String> userErrorMessage) {
        boolean isNull = actualValue == null;
        boolean isSuccess = isNullExpected.equals(isNull);

        String errorMessage = null;
        if (!isSuccess) {
            errorMessage = userErrorMessage.orElseGet(() -> {
                if (isNullExpected) {
                    return "expected '%s' to be null but was '%s'".formatted(actualValueQuery, actualValue);
                } else {
                    return "expected '%s' to be not null but was 'null'".formatted(actualValueQuery);
                }
            });
        }
        return new AssertionResult(
            operator.toString(),
            operator.toString(),
            actualValue,
            isSuccess,
            taskId.orElse(null),
            description.orElse(null),
            errorMessage
        );
    }

    private AssertionResult equalsTo(Object expectedValue, String actualValueQuery, Object actualValue, Optional<String> taskId, Optional<String> description, Optional<String> errorMessage) {
        var isSuccess = expectedValue.equals(actualValue);
        return new AssertionResult(
            EQUALS_TO.toString(),
            expectedValue,
            actualValue,
            isSuccess,
            taskId.orElse(null),
            description.orElse(null),
            isSuccess ? null : errorMessage.orElse("expected '%s' to equal '%s' but was '%s'"
                .formatted(actualValueQuery, expectedValue, actualValue))
        );
    }

    private AssertionResult notEqualsTo(Object expectedValue, String actualValueQuery, Object actualValue, Optional<String> taskId, Optional<String> description, Optional<String> errorMessage) {
        var isSuccess = !expectedValue.equals(actualValue);
        return new AssertionResult(
            NOT_EQUALS_TO.toString(),
            expectedValue,
            actualValue,
            isSuccess,
            taskId.orElse(null),
            description.orElse(null),
            isSuccess ? null : errorMessage.orElse("expected '%s' to not equal '%s'"
                .formatted(actualValueQuery, expectedValue.toString()))
        );
    }

    private AssertionResult endsWith(String expectedValue, String actualValueQuery, Object actualValue, Optional<String> taskId, Optional<String> description, Optional<String> errorMessage) {
        var isSuccess = ((String) actualValue).endsWith(expectedValue);
        return new AssertionResult(
            ENDS_WITH.toString(),
            expectedValue,
            actualValue,
            isSuccess,
            taskId.orElse(null),
            description.orElse(null),
            isSuccess ? null : errorMessage.orElse("expected '%s' to end with '%s' but was '%s'"
                .formatted(actualValueQuery, expectedValue, actualValue))
        );
    }

    private AssertionResult startsWith(String expectedValue, String actualValueQuery, Object actualValue, Optional<String> taskId, Optional<String> description, Optional<String> errorMessage) {
        var isSuccess = ((String) actualValue).startsWith(expectedValue);
        return new AssertionResult(
            STARTS_WITH.toString(),
            expectedValue,
            actualValue,
            isSuccess,
            taskId.orElse(null),
            description.orElse(null),
            isSuccess ? null : errorMessage.orElse("expected '%s' to start with '%s' but was '%s'"
                .formatted(actualValueQuery, expectedValue, actualValue))
        );
    }

    private AssertionResult contains(String expectedValue, String actualValueQuery, Object actualValue, Optional<String> taskId, Optional<String> description, Optional<String> errorMessage) {
        var isSuccess = ((String) actualValue).contains(expectedValue);
        return new AssertionResult(
            CONTAINS.toString(),
            expectedValue,
            actualValue,
            isSuccess,
            taskId.orElse(null),
            description.orElse(null),
            isSuccess ? null : errorMessage.orElse("expected '%s' to contain '%s' but was '%s'"
                .formatted(actualValueQuery, expectedValue, actualValue))
        );
    }

    private AssertionResult greaterThan(Double expectedValue, String actualValueQuery, Object actualValue, Optional<String> taskId, Optional<String> description, Optional<String> errorMessage) throws IllegalVariableEvaluationException {
        var isSuccess = tryToParseDouble(actualValue) > expectedValue;
        return new AssertionResult(
            GREATER_THAN.toString(),
            expectedValue,
            actualValue,
            isSuccess,
            taskId.orElse(null),
            description.orElse(null),
            isSuccess ? null : errorMessage.orElse("expected '%s' to be greater than '%s' but was '%s'"
                .formatted(actualValueQuery, expectedValue, actualValue))
        );
    }

    private AssertionResult greaterThanOrEqualTo(Double expectedValue, String actualValueQuery, Object actualValue, Optional<String> taskId, Optional<String> description, Optional<String> errorMessage) throws IllegalVariableEvaluationException {
        var isSuccess = tryToParseDouble(actualValue) >= expectedValue;
        return new AssertionResult(
            GREATER_THAN_OR_EQUAL_TO.toString(),
            expectedValue,
            actualValue,
            isSuccess,
            taskId.orElse(null),
            description.orElse(null),
            isSuccess ? null : errorMessage.orElse("expected '%s' to be greater than or equal to '%s' but was '%s'"
                .formatted(actualValueQuery, expectedValue, actualValue))
        );
    }

    private AssertionResult lessThan(Double expectedValue, String actualValueQuery, Object actualValue, Optional<String> taskId, Optional<String> description, Optional<String> errorMessage) throws IllegalVariableEvaluationException {
        var isSuccess = tryToParseDouble(actualValue) < expectedValue;
        return new AssertionResult(
            LESS_THAN.toString(),
            expectedValue,
            actualValue,
            isSuccess,
            taskId.orElse(null),
            description.orElse(null),
            isSuccess ? null : errorMessage.orElse("expected '%s' to be less than '%s' but was '%s'"
                .formatted(actualValueQuery, expectedValue, actualValue))
        );
    }

    private AssertionResult lessThanOrEqualTo(Double expectedValue, String actualValueQuery, Object actualValue, Optional<String> taskId, Optional<String> description, Optional<String> errorMessage) throws IllegalVariableEvaluationException {
        var isSuccess = tryToParseDouble(actualValue) <= expectedValue;
        return new AssertionResult(
            LESS_THAN_OR_EQUAL_TO.toString(),
            expectedValue,
            actualValue,
            isSuccess,
            taskId.orElse(null),
            description.orElse(null),
            isSuccess ? null : errorMessage.orElse("expected '%s' to be less than or equal to '%s' but was '%s'"
                .formatted(actualValueQuery, expectedValue, actualValue))
        );
    }

    private Double tryToParseDouble(Object x) throws IllegalVariableEvaluationException {
        if (x instanceof Double) {
            return (Double) x;
        } else {
            try {
                return Double.parseDouble(x.toString());
            } catch (NumberFormatException e) {
                throw new IllegalVariableEvaluationException("Could not parse value as a Double");
            }
        }
    }

    private AssertionResult in(List<String> expectedInList, String actualValueQuery, Object actualValue, Optional<String> taskId, Optional<String> description, Optional<String> errorMessage) {
        var isSuccess = expectedInList.stream().anyMatch(actualValue::equals);
        return new AssertionResult(
            IN.toString(),
            expectedInList,
            actualValue,
            isSuccess,
            taskId.orElse(null),
            description.orElse(null),
            isSuccess ? null : errorMessage.orElse("expected '%s' to contain '%s' but was '%s'"
                .formatted(actualValueQuery, expectedInList, actualValue))
        );
    }

    private AssertionResult notIn(List<String> notExpectedInList, String actualValueQuery, Object actualValue, Optional<String> taskId, Optional<String> description, Optional<String> errorMessage) {
        var isSuccess = notExpectedInList.stream().noneMatch(actualValue::equals);
        return new AssertionResult(
            NOT_IN.toString(),
            notExpectedInList,
            actualValue,
            isSuccess,
            taskId.orElse(null),
            description.orElse(null),
            isSuccess ? null : errorMessage.orElse("expected '%s' to not contain '%s' but was '%s'"
                .formatted(actualValueQuery, notExpectedInList, actualValue))
        );
    }
}
