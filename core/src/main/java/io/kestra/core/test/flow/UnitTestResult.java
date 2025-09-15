package io.kestra.core.test.flow;


import io.kestra.core.test.TestState;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.net.URI;
import java.util.List;

public record UnitTestResult(
    @NotNull
    String testId,
    @NotNull
    String testType,
    String executionId,
    URI url,
    @NotNull
    TestState state,
    @NotNull
    List<AssertionResult> assertionResults,
    @NotNull
    List<AssertionRunError> errors,
    Fixtures fixtures
) {
    public static UnitTestResult of(String unitTestId, String unitTestType, String executionId, URI url, List<AssertionResult> results, List<AssertionRunError> errors, @Nullable Fixtures fixtures) {
        TestState state;
        if(!errors.isEmpty()){
            state = TestState.ERROR;
        } else {
            state = results.stream().anyMatch(assertion -> !assertion.isSuccess()) ? TestState.FAILED : TestState.SUCCESS;
        }
        return new UnitTestResult(unitTestId, unitTestType, executionId, url, state, results, errors, fixtures);
    }

    public static UnitTestResult ofDisabled(String unitTestId, String unitTestType, @Nullable Fixtures fixtures) {
        return new UnitTestResult(unitTestId, unitTestType, null, null, TestState.SKIPPED, List.of(), List.of(), fixtures);
    }
}
