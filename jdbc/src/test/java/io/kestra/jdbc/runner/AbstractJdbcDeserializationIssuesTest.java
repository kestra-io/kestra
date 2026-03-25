package io.kestra.jdbc.runner;

import java.time.LocalDateTime;
import java.util.*;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.google.common.base.CaseFormat;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.runners.DefaultFlowMetaStore;
import io.kestra.core.runners.DeserializationIssuesCaseTest;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTableConfigs;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.queue.jdbc.client.JdbcQueueClient;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

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

        dslContextWrapper.transaction(configuration ->
        {
            DSLContext context = DSL.using(configuration);

            context
                .insertInto(table)
                .set(fields)
                .execute();
        });
    }

    protected Map<Field<Object>, Object> fields(DeserializationIssuesCaseTest.QueueMessage queueMessage) {
        String queueName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, queueMessage.type().getSimpleName());
        Map<Field<Object>, Object> fields = new HashMap<>();
        fields.put(AbstractJdbcRepository.field("type"), JdbcQueueClient.queueNameToType(queueName));
        fields.put(AbstractJdbcRepository.field("key"), queueMessage.key() != null ? queueMessage.key() : IdUtils.create());
        fields.put(AbstractJdbcRepository.field("value"), JSONB.valueOf(queueMessage.value()));
        fields.put(AbstractJdbcRepository.field("created"), LocalDateTime.now());
        return fields;
    }

    // As the flow queue is on dispatch, to be able to consume it in a test we should replace the FlowMetaStore by a no-op implementation
    @MockBean
    @Replaces(DefaultFlowMetaStore.class)
    FlowMetaStoreInterface noOp() {
        return new FlowMetaStoreInterface() {
            @Override
            public boolean isNamespaceExists(String tenant, String namespace) {
                return false;
            }

            @Override
            public Collection<FlowWithSource> allLastVersion() {
                return List.of();
            }

            @Override
            public Optional<FlowInterface> findById(String tenantId, String namespace, String id, Optional<Integer> revision) {
                return Optional.empty();
            }

            @Override
            public Optional<FlowWithSource> findByExecutionThenInjectDefaults(Execution execution) {
                return Optional.empty();
            }
        };
    }
}
