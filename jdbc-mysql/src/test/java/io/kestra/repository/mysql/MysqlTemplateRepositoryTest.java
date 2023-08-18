package io.kestra.repository.mysql;

import io.kestra.jdbc.repository.AbstractJdbcTemplateRepositoryTest;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.util.StringUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Property(name = "kestra.templates.enabled", value = StringUtils.TRUE)
public class MysqlTemplateRepositoryTest extends AbstractJdbcTemplateRepositoryTest {
    @Test
    @Disabled("TODO: Seems to have issue with autocommit on mysql ?")
    void find() {

    }
}
