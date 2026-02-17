package io.kestra.jdbc.runner;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.DeserializationIssuesCaseTest;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTableConfigs;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import jakarta.inject.Inject;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.Map;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// must be per-class to allow calling once init() which took a lot of time
public abstract class AbstractJdbcDeserializationIssuesTest {
    @Inject
    private DeserializationIssuesCaseTest deserializationIssuesCaseTest;

    @Inject
    private JooqDSLContextWrapper dslContextWrapper;

    @Inject
    private JdbcTableConfigs jdbcTableConfigs;

    @Test
    void workerTaskDeserializationIssue() throws Exception {
        deserializationIssuesCaseTest.workerTaskDeserializationIssue(this::sendToQueue);
    }

    @Test
    void workerTriggerDeserializationIssue() throws Exception {
        deserializationIssuesCaseTest.workerTriggerDeserializationIssue(this::sendToQueue);
    }

    @Test
    void flowDeserializationIssue() throws Exception {
        deserializationIssuesCaseTest.flowDeserializationIssue(this::sendToQueue);
    }

    private void sendToQueue(DeserializationIssuesCaseTest.QueueMessage queueMessage) {

        Table<Record> table = DSL.table(jdbcTableConfigs.tableConfig("queues").table());

        Map<Field<Object>, Object> fields = fields(queueMessage);

        dslContextWrapper.transaction(configuration -> {
            DSLContext context = DSL.using(configuration);

            context
                .insertInto(table)
                .set(fields)
                .execute();
        });
    }

    protected Map<Field<Object>, Object> fields(DeserializationIssuesCaseTest.QueueMessage queueMessage) {
        Map<Field<Object>, Object> fields = new HashMap<>();
        fields.put(AbstractJdbcRepository.field("type"), queueMessage.type().getName());
        fields.put(AbstractJdbcRepository.field("key"), queueMessage.key() != null ? queueMessage.key() : IdUtils.create());
        fields.put(AbstractJdbcRepository.field("value"), JSONB.valueOf(queueMessage.value()));
        return fields;
    }
}
