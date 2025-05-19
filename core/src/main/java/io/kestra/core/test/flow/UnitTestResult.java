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
    TestState state,
    @NotNull
    List<AssertionResult> assertionResults,
    Fixtures fixtures
) {

    public static UnitTestResult of(String unitTestId, String unitTestType, List<AssertionResult> results, @Nullable Fixtures fixtures) {
        var state = results.stream().anyMatch(assertion -> !assertion.isSuccess()) ? TestState.FAILED : TestState.SUCCESS;
        return new UnitTestResult(unitTestId, unitTestType, state, results, fixtures);
    }
}
