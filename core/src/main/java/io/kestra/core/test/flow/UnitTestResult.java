package io.kestra.core.test.flow;


import io.kestra.core.test.TestState;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UnitTestResult(
    @NotNull
    String unitTestId,
    @NotNull
    String unitTestType,
    @NotNull
    String executionId,
    @NotNull
    TestState state,
    @NotNull
    List<AssertionResult> assertionResults,
    @NotNull
    List<AssertionRunError> errors,
    Fixtures fixtures
) {

    public static UnitTestResult of(String unitTestId, String unitTestType, String executionId, List<AssertionResult> results, List<AssertionRunError> errors, @Nullable Fixtures fixtures) {
        TestState state;
        if(!errors.isEmpty()){
            state = TestState.ERROR;
        } else {
            state = results.stream().anyMatch(assertion -> !assertion.isSuccess()) ? TestState.FAILED : TestState.SUCCESS;
        }
        return new UnitTestResult(unitTestId, unitTestType, executionId, state, results, errors, fixtures);
    }
}
