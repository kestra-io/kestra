package io.kestra.core.test;

import io.kestra.core.test.flow.UnitTestResult;

import java.util.List;


public record TestSuiteRunResult(
    String id,
    String testSuiteId,
    TestState state,
    List<UnitTestResult> results) {

    public static TestSuiteRunResult of(String id, String testSuiteId, List<UnitTestResult> results) {
        var state = results.stream().anyMatch(result -> result.state().equals(TestState.FAILED)) ? TestState.FAILED : TestState.SUCCESS;
        return new TestSuiteRunResult(id, testSuiteId, state, results);
    }
}
