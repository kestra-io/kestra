package io.kestra.core.test.flow;


import io.kestra.core.test.TestState;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UnitTestResult(
    @NotNull
    String unitTestId,
    @NotNull
    String unitTestType,
    @NotNull
    TestState state,
    @NotNull
    List<AssertionResult> assertionResults
) {

    public static UnitTestResult of(String unitTestId, String unitTestType, List<AssertionResult> results) {
        var state = results.stream().anyMatch(assertion -> !assertion.isSuccess()) ? TestState.FAILED : TestState.SUCCESS;
        return new UnitTestResult(unitTestId, unitTestType, state, results);
    }
}
