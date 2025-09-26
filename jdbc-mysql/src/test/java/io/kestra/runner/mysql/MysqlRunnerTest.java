package io.kestra.runner.mysql;

import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.jdbc.runner.JdbcRunnerTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class MysqlRunnerTest extends JdbcRunnerTest {

    @Disabled("We have a bug here in the queue where no FAILED event is sent, so the state store is not cleaned")
    @Test
    @LoadFlows({"flows/valids/restart-with-finally.yaml"})
    protected void restartFailedWithFinally() throws Exception {
        restartCaseTest.restartFailedWithFinally();
    }
}
