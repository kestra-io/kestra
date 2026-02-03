package io.kestra.core.test;

import io.kestra.core.test.flow.UnitTestResult;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
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
    @NotNull
    Instant startDate,
    @NotNull
    Instant endDate,
    List<UnitTestResult> results
) {

    public static TestSuiteRunResult of(String id, String testSuiteId, String namespace, String flowId, Instant startDate, Instant endDate, List<UnitTestResult> results) {
        boolean allSkipped = true;
        boolean anyFailed = false;
        for (UnitTestResult result : results) {
            if(!result.state().equals(TestState.SKIPPED)) {
                allSkipped = false;
            }
            if (result.state().equals(TestState.FAILED)) {
                anyFailed = true;
            }
            if (result.state().equals(TestState.ERROR)) {
                return new TestSuiteRunResult(id, testSuiteId, namespace, flowId, result.state(), startDate, endDate, results);
            }
        }
        if (anyFailed) {
            return new TestSuiteRunResult(id, testSuiteId, namespace, flowId, TestState.FAILED, startDate, endDate, results);
        }
        var state = allSkipped ? TestState.SKIPPED : TestState.SUCCESS;
        return new TestSuiteRunResult(id, testSuiteId, namespace, flowId, state, startDate, endDate, results);
    }

    public static TestSuiteRunResult ofDisabledTestSuite(String id, String testSuiteId, String namespace, String flowId) {
        var now = Instant.now();
        return new TestSuiteRunResult(id, testSuiteId, namespace, flowId, TestState.SKIPPED, now, now, List.of());
    }
}
