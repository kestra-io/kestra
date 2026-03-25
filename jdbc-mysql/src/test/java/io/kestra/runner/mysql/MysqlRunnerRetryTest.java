package io.kestra.runner.mysql;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import io.kestra.core.runners.AbstractRunnerRetryTest;

@TestInstance(Lifecycle.PER_CLASS)
public class MysqlRunnerRetryTest extends AbstractRunnerRetryTest {

}
