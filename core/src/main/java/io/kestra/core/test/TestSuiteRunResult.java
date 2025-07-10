package io.kestra.core.test;

import io.kestra.core.test.flow.UnitTestResult;
import jakarta.validation.constraints.NotNull;

import java.util.List;


public record TestSuiteRunResult(
    @NotNull
    String id,
    @NotNull
    String testSuiteId,
    @NotNull
    String namespace,
    @NotNull
    String flowId,
    @NotNull
    TestState state,
    List<UnitTestResult> results
) {

    public static TestSuiteRunResult of(String id, String testSuiteId, String namespace, String flowId, List<UnitTestResult> results) {
        for (UnitTestResult result : results) {
            if(result.state().equals(TestState.ERROR) || result.state().equals(TestState.FAILED)) {
                return new TestSuiteRunResult(id, testSuiteId, namespace, flowId, result.state(), results);
            }
        }
        return new TestSuiteRunResult(id, testSuiteId, namespace, flowId, TestState.SUCCESS, results);
    }

    public static TestSuiteRunResult ofDisabledTestSuite(String id, String testSuiteId, String namespace, String flowId) {
        return new TestSuiteRunResult(id, testSuiteId, namespace, flowId, TestState.SKIPPED, List.of());
    }
}
