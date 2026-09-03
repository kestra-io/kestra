package io.kestra.jdbc.repository;

import java.sql.Timestamp;
import java.time.Instant;

import org.jooq.impl.DSL;

import io.kestra.core.mcp.repositories.AbstractMcpSessionRepositoryTest;
import io.kestra.jdbc.JooqDSLContextWrapper;

import jakarta.inject.Inject;

public abstract class AbstractJdbcMcpSessionRepositoryTest extends AbstractMcpSessionRepositoryTest {

    @Inject
    private JooqDSLContextWrapper dslContextWrapper;

    @Override
    protected void backdateCreatedAt(String sessionId, Instant timestamp) {
        dslContextWrapper.transaction(
            config -> DSL.using(config)
                .update(DSL.table("mcp_session"))
                .set(DSL.field(DSL.name("created_at")), Timestamp.from(timestamp))
                .where(DSL.field(DSL.name("session_id")).eq(sessionId))
                .execute()
        );
    }
}
