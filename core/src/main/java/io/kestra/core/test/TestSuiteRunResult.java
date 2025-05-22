package io.kestra.core.test;

import io.kestra.core.test.flow.UnitTestResult;

import java.util.List;


public record TestSuiteRunResult(
    String id,
    String testSuiteId,
    TestState state,
    List<UnitTestResult> results) {

    public static TestSuiteRunResult of(String id, String testSuiteId, List<UnitTestResult> results) {
        for (UnitTestResult result : results) {
            if(result.state().equals(TestState.ERROR) || result.state().equals(TestState.FAILED)) {
                return new TestSuiteRunResult(id, testSuiteId, result.state(), results);
            }
        }
        return new TestSuiteRunResult(id, testSuiteId, TestState.SUCCESS, results);
    }
}
