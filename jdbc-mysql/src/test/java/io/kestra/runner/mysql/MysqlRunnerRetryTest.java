package io.kestra.runner.mysql;

import io.kestra.jdbc.runner.JdbcRunnerRetryTest;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class MysqlRunnerRetryTest extends JdbcRunnerRetryTest {

}
