package io.kestra.mcp;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

public interface McpToolServiceAllTest {

    @MicronautTest(environments = { "test", "mysql" }, transactional = false)
    class MysqlMcpToolServiceTest extends McpToolServiceTest {
    }

    @MicronautTest(environments = { "test", "postgres" }, transactional = false)
    class PostgresMcpToolServiceTest extends McpToolServiceTest {
    }

    @MicronautTest(environments = { "test", "h2" }, transactional = false)
    class H2McpToolServiceTest extends McpToolServiceTest {
    }
}
