package io.kestra.core.test;

import io.kestra.core.test.flow.AssertionResult;
import io.kestra.core.test.flow.AssertionRunError;
import io.kestra.core.test.flow.UnitTestResult;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSuiteRunResultTest {

    private static final AssertionResult SUCCESSFUL_ASSERTION = new AssertionResult("operator", "val", "val", true, null, null, null);
    private static final AssertionResult FAILING_ASSERTION = new AssertionResult("operator", "val", "val", false, null, null, null);

    @Test
    void success() {
        var res = TestSuiteRunResult.of("id", "testSuiteId", "namespace", "flowId", Instant.now(), Instant.now(),
            List.of(
                UnitTestResult.of("id", "type", "executionId", URI.create("url"),
                    List.of(
                        SUCCESSFUL_ASSERTION
                    ),
                    List.of(),
                    null
                )
            )
        );
        assertThat(res).extracting(TestSuiteRunResult::state).isEqualTo(TestState.SUCCESS);
    }

    @Test
    void disabled() {
        var res = TestSuiteRunResult.ofDisabledTestSuite("id", "testSuiteId", "namespace", "flowId");
        assertThat(res).extracting(TestSuiteRunResult::state).isEqualTo(TestState.SKIPPED);
    }

    @Test
    void one_assertion_failed() {
        var res = TestSuiteRunResult.of("id", "testSuiteId", "namespace", "flowId", Instant.now(), Instant.now(),
            List.of(
                UnitTestResult.of("id", "type", "executionId", URI.create("url"),
                    List.of(
                        SUCCESSFUL_ASSERTION,
                        FAILING_ASSERTION,
                        SUCCESSFUL_ASSERTION
                    ),
                    List.of(),
                    null
                )
            )
        );
        assertThat(res).extracting(TestSuiteRunResult::state).isEqualTo(TestState.FAILED);
    }

    @Test
    void one_testcase_failed() {
        var res = TestSuiteRunResult.of("id", "testSuiteId", "namespace", "flowId", Instant.now(), Instant.now(),
            List.of(
                UnitTestResult.of("id", "type", "executionId", URI.create("url"),
                    List.of(
                        SUCCESSFUL_ASSERTION
                    ),
                    List.of(),
                    null
                ),
                UnitTestResult.of("id", "type", "executionId", URI.create("url"),
                    List.of(
                        FAILING_ASSERTION
                    ),
                    List.of(),
                    null
                )
            )
        );
        assertThat(res).extracting(TestSuiteRunResult::state).isEqualTo(TestState.FAILED);
    }

    @Test
    void one_testcase_error() {
        var res = TestSuiteRunResult.of("id", "testSuiteId", "namespace", "flowId", Instant.now(), Instant.now(),
            List.of(
                UnitTestResult.of("id", "type", "executionId", URI.create("url"),
                    List.of(
                        SUCCESSFUL_ASSERTION
                    ),
                    List.of(),
                    null
                ),
                UnitTestResult.of("id", "type", "executionId", URI.create("url"),
                    List.of(
                        FAILING_ASSERTION
                    ),
                    List.of(),
                    null
                ),
                UnitTestResult.of("id", "type", "executionId", URI.create("url"),
                    List.of(),
                    List.of(new AssertionRunError("assertion failed", "assertion failed details")),
                    null
                )
            )
        );
        assertThat(res).extracting(TestSuiteRunResult::state).isEqualTo(TestState.ERROR);
    }

    @Test
    void one_testcase_skipped() {
        var skippedTestcaseId = "skipped_testcase_id";
        var res = TestSuiteRunResult.of("id", "testSuiteId", "namespace", "flowId", Instant.now(), Instant.now(),
            List.of(
                UnitTestResult.of("id", "type", "executionId", URI.create("url"),
                    List.of(
                        SUCCESSFUL_ASSERTION
                    ),
                    List.of(),
                    null
                ),
                UnitTestResult.ofDisabled(skippedTestcaseId, "type", null)
            )
        );
        assertThat(res).extracting(TestSuiteRunResult::state).isEqualTo(TestState.SUCCESS);

        assertThat(res.results())
            .filteredOn(testcase -> skippedTestcaseId.equals(testcase.testId()))
            .first()
            .extracting(UnitTestResult::state)
            .isEqualTo(TestState.SKIPPED);
    }

    @Test
    void all_testcases_skipped() {
        var res = TestSuiteRunResult.of("id", "testSuiteId", "namespace", "flowId", Instant.now(), Instant.now(),
            List.of(
                UnitTestResult.ofDisabled("id", "type", null),
                UnitTestResult.ofDisabled("id", "type", null)
            )
        );
        assertThat(res).extracting(TestSuiteRunResult::state).isEqualTo(TestState.SKIPPED);
    }

    @Test
    void testcase_skipped() {
        var res = TestSuiteRunResult.of("id", "testSuiteId", "namespace", "flowId", Instant.now(), Instant.now(),
            List.of(
                UnitTestResult.ofDisabled("id", "type", null)
            )
        );
        assertThat(res).extracting(TestSuiteRunResult::state).isEqualTo(TestState.SKIPPED);
    }
}
