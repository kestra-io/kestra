package io.kestra.runner.h2;

import io.kestra.jdbc.runner.JdbcTemplateRunnerTest;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.util.StringUtils;

@Property(name = "kestra.templates.enabled", value = StringUtils.TRUE)
public class H2TemplateRunnerTest extends JdbcTemplateRunnerTest {

}
