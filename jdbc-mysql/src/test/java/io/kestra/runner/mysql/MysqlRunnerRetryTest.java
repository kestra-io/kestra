package io.kestra.runner.mysql;

import io.kestra.jdbc.runner.JdbcRunnerRetryTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Disabled("Deadlock :(")
@TestInstance(Lifecycle.PER_CLASS)
public class MysqlRunnerRetryTest extends JdbcRunnerRetryTest {

}
