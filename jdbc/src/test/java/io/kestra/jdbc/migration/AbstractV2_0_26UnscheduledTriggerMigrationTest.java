package io.kestra.jdbc.migration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract integration tests for {@link V2_0_26UnscheduledTriggerMigration}.
 * Subclassed per JDBC backend (H2, Postgres, MySQL).
 */
@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
public abstract class AbstractV2_0_26UnscheduledTriggerMigrationTest {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final String TENANT_ID = "main";
    private static final String NAMESPACE = "io.kestra.test";
    private static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));
    private static final Field<Object> SOURCE_FIELD = DSL.field(DSL.quotedName("source_code"));

    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    @Inject
    V2_0_26UnscheduledTriggerMigration migration;

    @BeforeEach
    void cleanup() {
        dslContextWrapper.transaction(configuration ->
        {
            DSL.using(configuration).deleteFrom(DSL.table("triggers")).execute();
            DSL.using(configuration).deleteFrom(DSL.table("flows")).execute();
        });
    }

    @Test
    void shouldCreateStateForWebhookMcpAndFlowTriggersButNotForSchedule() throws Exception {
        // Given
        insertFlow("listed", 1, false, """
            id: listed
            namespace: %s
            tasks:
              - id: task
                type: io.kestra.plugin.core.log.Log
                message: hello
            triggers:
              - id: schedule
                type: io.kestra.plugin.core.trigger.Schedule
                cron: "0 0 * * *"
              - id: webhook
                type: io.kestra.plugin.core.trigger.Webhook
                key: a-key
              - id: mcp
                type: io.kestra.plugin.core.trigger.McpToolTrigger
              - id: flow-trigger
                type: io.kestra.plugin.core.trigger.Flow
            """.formatted(NAMESPACE));

        // When
        migration.migrate();

        // Then only the triggers the scheduler does not evaluate get a state, all typed UNSCHEDULED
        assertThat(triggerIds()).containsExactlyInAnyOrder("webhook", "mcp", "flow-trigger");
        assertThat(readTriggerState("listed", "webhook").getType()).isEqualTo(TriggerType.UNSCHEDULED);
        assertThat(readTriggerState("listed", "webhook").getNextEvaluationDate()).isNull();
    }

    @Test
    void shouldCarryTheDisabledFlagFromTheFlowSource() throws Exception {
        // Given
        insertFlow("disabled-webhook", 1, false, """
            id: disabled-webhook
            namespace: %s
            tasks:
              - id: task
                type: io.kestra.plugin.core.log.Log
                message: hello
            triggers:
              - id: webhook
                type: io.kestra.plugin.core.trigger.Webhook
                key: a-key
                disabled: true
            """.formatted(NAMESPACE));

        // When
        migration.migrate();

        // Then
        assertThat(readTriggerState("disabled-webhook", "webhook").isDisabled()).isTrue();
    }

    @Test
    void shouldSkipDeletedFlowsAndOlderRevisions() throws Exception {
        // Given a deleted flow, and one whose webhook trigger only exists on an older revision
        insertFlow("deleted-flow", 1, true, """
            id: deleted-flow
            namespace: %s
            tasks:
              - id: task
                type: io.kestra.plugin.core.log.Log
                message: hello
            triggers:
              - id: webhook
                type: io.kestra.plugin.core.trigger.Webhook
                key: a-key
            """.formatted(NAMESPACE));
        insertFlow("revised-flow", 1, false, """
            id: revised-flow
            namespace: %s
            tasks:
              - id: task
                type: io.kestra.plugin.core.log.Log
                message: hello
            triggers:
              - id: old-webhook
                type: io.kestra.plugin.core.trigger.Webhook
                key: a-key
            """.formatted(NAMESPACE));
        insertFlow("revised-flow", 2, false, """
            id: revised-flow
            namespace: %s
            tasks:
              - id: task
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(NAMESPACE));

        // When
        migration.migrate();

        // Then
        assertThat(triggerIds()).isEmpty();
    }

    @Test
    void shouldLeaveAnExistingStateUntouchedWhenRunTwice() throws Exception {
        // Given
        insertFlow("rerun", 1, false, """
            id: rerun
            namespace: %s
            tasks:
              - id: task
                type: io.kestra.plugin.core.log.Log
                message: hello
            triggers:
              - id: webhook
                type: io.kestra.plugin.core.trigger.Webhook
                key: a-key
            """.formatted(NAMESPACE));
        migration.migrate();
        TriggerState first = readTriggerState("rerun", "webhook");

        // When
        migration.migrate();

        // Then
        assertThat(triggerIds()).containsExactly("webhook");
        assertThat(readTriggerState("rerun", "webhook").getUpdatedAt()).isEqualTo(first.getUpdatedAt());
    }

    @Test
    void shouldHandleAnEmptyFlowsTable() throws Exception {
        // When / Then — no exception
        migration.migrate();
        assertThat(triggerIds()).isEmpty();
    }

    @Test
    void shouldReturnCorrectMetadata() {
        assertThat(migration.scriptId()).isEqualTo("2.0.26-unscheduled-triggers");
        assertThat(migration.description()).isNotBlank();
        assertThat(migration.checksum()).isNull();
    }

    // --- Helpers ---

    private void insertFlow(String flowId, int revision, boolean deleted, String source) throws Exception {
        Map<String, Object> flow = new LinkedHashMap<>();
        flow.put("tenantId", TENANT_ID);
        flow.put("namespace", NAMESPACE);
        flow.put("id", flowId);
        flow.put("revision", revision);
        flow.put("deleted", deleted);

        String json = MAPPER.writeValueAsString(flow);
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .insertInto(DSL.table("flows"))
                .set(KEY_FIELD, (Object) (TENANT_ID + "_" + NAMESPACE + "_" + flowId + "_" + revision))
                .set(VALUE_FIELD, (Object) JSONB.valueOf(json))
                .set(SOURCE_FIELD, (Object) source)
                .execute()
        );
    }

    private List<String> triggerIds() {
        return dslContextWrapper.transactionResult(
            configuration -> DSL.using(configuration)
                .select(DSL.field(DSL.quotedName("trigger_id")))
                .from(DSL.table("triggers"))
                .fetch(DSL.field(DSL.quotedName("trigger_id")), String.class)
        );
    }

    private TriggerState readTriggerState(String flowId, String triggerId) {
        String key = TriggerId.of(TENANT_ID, NAMESPACE, flowId, triggerId).uid();
        return dslContextWrapper.transactionResult(configuration ->
        {
            String json = DSL.using(configuration)
                .select(VALUE_FIELD)
                .from(DSL.table("triggers"))
                .where(KEY_FIELD.eq(key))
                .fetchOne(VALUE_FIELD, String.class);

            try {
                return MAPPER.readValue(json, TriggerState.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
