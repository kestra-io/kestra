package io.kestra.repository.mysql;

import io.kestra.jdbc.repository.AbstractJdbcTemplateRepositoryTest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@MicronautTest
public class MysqlTemplateRepositoryTest extends AbstractJdbcTemplateRepositoryTest {
    @Test
    @Disabled("TODO: Seems to have issue with autocommit on mysql ?")
    void find() {

    }
}
