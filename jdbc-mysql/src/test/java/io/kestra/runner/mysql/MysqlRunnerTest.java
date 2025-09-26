package io.kestra.runner.mysql;

import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.jdbc.runner.JdbcRunnerTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class MysqlRunnerTest extends JdbcRunnerTest {

    @Disabled("We have a bug here in the queue where no FAILED event is sent, so the state store is not cleaned")
    @Test
    @Override
    @LoadFlows({"flows/valids/restart-with-finally.yaml"})
    protected void restartFailedWithFinally() throws Exception {
        restartCaseTest.restartFailedWithFinally();
    }

    @Disabled("Should fail the second time, but is success")
    @Test
    @Override
    @LoadFlows({"flows/valids/restart_local_errors.yaml"})
    protected void restartFailedThenFailureWithLocalErrors() throws Exception {
        restartCaseTest.restartFailedThenFailureWithLocalErrors();
    }

    @Disabled("Is success, but is not terminated")
    @Test
    @Override
    @LoadFlows({"flows/valids/restart-with-after-execution.yaml"})
    protected void restartFailedWithAfterExecution() throws Exception {
        restartCaseTest.restartFailedWithAfterExecution();
    }
}
