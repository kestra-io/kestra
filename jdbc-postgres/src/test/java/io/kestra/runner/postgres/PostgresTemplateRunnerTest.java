package io.kestra.runner.postgres;

import io.kestra.jdbc.runner.JdbcTemplateRunnerTest;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.util.StringUtils;

@Property(name = "kestra.templates.enabled", value = StringUtils.TRUE)
public class PostgresTemplateRunnerTest extends JdbcTemplateRunnerTest {

}
