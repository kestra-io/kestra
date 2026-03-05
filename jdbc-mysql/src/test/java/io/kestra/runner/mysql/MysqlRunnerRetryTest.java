package io.kestra.runner.mysql;

import io.kestra.core.runners.AbstractRunnerRetryTest;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class MysqlRunnerRetryTest extends AbstractRunnerRetryTest {

}
