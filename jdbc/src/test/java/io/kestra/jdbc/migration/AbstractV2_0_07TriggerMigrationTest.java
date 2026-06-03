package io.kestra.jdbc.migration;

import java.time.Instant;
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
import io.kestra.core.scheduler.vnodes.VNodes;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract integration tests for {@link V2_0_07TriggerMigration}.
 * Subclassed per JDBC backend (H2, Postgres, MySQL).
 */
@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
public abstract class AbstractV2_0_07TriggerMigrationTest {

    private static final ObjectMapper MAPPER = JdbcMapper.of();
    private static final int VNODES = 16;
    private static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));

    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    @Inject
    V2_0_07TriggerMigration migration;

    @BeforeEach
    void cleanup() {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .deleteFrom(DSL.table("triggers"))
                .execute()
        );
    }

    @Test
    void shouldMigrateV1TriggerWithAllFields() throws Exception {
        // Given
        String tenantId = "test-tenant";
        String namespace = "io.kestra.test";
        String flowId = "my-flow";
        String triggerId = "daily-schedule";

        Map<String, Object> v1Json = new LinkedHashMap<>();
        v1Json.put("tenantId", tenantId);
        v1Json.put("namespace", namespace);
        v1Json.put("flowId", flowId);
        v1Json.put("triggerId", triggerId);
        v1Json.put("date", "2024-06-15T10:30:00.000+02:00");
        v1Json.put("nextExecutionDate", "2024-06-16T10:30:00.000+02:00");
        v1Json.put("executionId", "exec-abc-123");
        v1Json.put("updatedDate", "2024-06-15T08:00:00.000000Z");
        v1Json.put("workerId", "worker-1");
        v1Json.put("disabled", true);
        v1Json.put("stopAfter", List.of("FAILED", "WARNING"));

        String key = tenantId + "_" + namespace + "_" + flowId + "_" + triggerId;
        insertV1Trigger(key, v1Json);

        // When
        migration.migrate();

        // Then
        TriggerState result = readTriggerState(key);
        assertThat(result.getNamespace()).isEqualTo(namespace);
        assertThat(result.getFlowId()).isEqualTo(flowId);
        assertThat(result.getTriggerId()).isEqualTo(triggerId);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getWorkerId()).isEqualTo("worker-1");
        assertThat(result.isDisabled()).isTrue();
        assertThat(result.isLocked()).isTrue();
        assertThat(result.getEvaluatedAt()).isNotNull();
        assertThat(result.getNextEvaluationDate()).isNotNull();
        assertThat(result.getUpdatedAt()).isEqualTo(Instant.parse("2024-06-15T08:00:00Z"));

        int expectedVnode = VNodes.computeVNodeFromTrigger(
            TriggerId.of(tenantId, namespace, flowId, triggerId), VNODES
        );
        assertThat(result.getVnode()).isEqualTo(expectedVnode);
    }

    @Test
    void shouldMigrateV1TriggerWithNullOptionalFields() throws Exception {
        // Given
        Map<String, Object> v1Json = new LinkedHashMap<>();
        v1Json.put("namespace", "io.kestra.test");
        v1Json.put("flowId", "minimal-flow");
        v1Json.put("triggerId", "trigger-1");
        v1Json.put("disabled", false);

        String key = "null_io.kestra.test_minimal-flow_trigger-1";
        insertV1Trigger(key, v1Json);

        // When
        migration.migrate();

        // Then
        TriggerState result = readTriggerState(key);
        assertThat(result.getTenantId()).isNull();
        assertThat(result.getEvaluatedAt()).isNull();
        assertThat(result.getNextEvaluationDate()).isNull();
        assertThat(result.isLocked()).isFalse();
        assertThat(result.isDisabled()).isFalse();
        assertThat(result.getWorkerId()).isNull();
        assertThat(result.getUpdatedAt()).isNull();
        assertThat(result.getBackfill()).isNull();
        assertThat(result.getStopAfter()).isNull();
    }

    @Test
    void shouldMigrateMultipleTriggers() throws Exception {
        // Given
        for (int i = 0; i < 5; i++) {
            Map<String, Object> v1Json = new LinkedHashMap<>();
            v1Json.put("namespace", "io.kestra.batch");
            v1Json.put("flowId", "flow-" + i);
            v1Json.put("triggerId", "trigger-" + i);
            v1Json.put("disabled", false);
            insertV1Trigger("key-" + i, v1Json);
        }

        // When
        migration.migrate();

        // Then
        for (int i = 0; i < 5; i++) {
            TriggerState result = readTriggerState("key-" + i);
            assertThat(result.getFlowId()).isEqualTo("flow-" + i);
            assertThat(result.getTriggerId()).isEqualTo("trigger-" + i);
        }
    }

    @Test
    void shouldHandleEmptyTable() throws Exception {
        // When / Then — no exception
        migration.migrate();
    }

    @Test
    void shouldSetLockedFalseWhenNoExecutionId() throws Exception {
        // Given
        Map<String, Object> v1Json = new LinkedHashMap<>();
        v1Json.put("namespace", "io.kestra.test");
        v1Json.put("flowId", "no-exec-flow");
        v1Json.put("triggerId", "t1");
        v1Json.put("executionId", null);
        v1Json.put("disabled", false);
        insertV1Trigger("no-exec-key", v1Json);

        // When
        migration.migrate();

        // Then
        TriggerState result = readTriggerState("no-exec-key");
        assertThat(result.isLocked()).isFalse();
    }

    @Test
    void shouldPreserveExtraFieldsInV1JsonViaIgnoreUnknown() throws Exception {
        // Given — V1 JSON with extra unknown fields
        Map<String, Object> v1Json = new LinkedHashMap<>();
        v1Json.put("namespace", "io.kestra.test");
        v1Json.put("flowId", "extra-fields-flow");
        v1Json.put("triggerId", "t1");
        v1Json.put("disabled", false);
        v1Json.put("someUnknownField", "should-be-ignored");
        v1Json.put("anotherExtra", 42);
        insertV1Trigger("extra-key", v1Json);

        // When / Then — no error despite unknown fields
        migration.migrate();

        TriggerState result = readTriggerState("extra-key");
        assertThat(result.getFlowId()).isEqualTo("extra-fields-flow");
    }

    @Test
    void shouldPreserveBackfillData() throws Exception {
        // Given
        Map<String, Object> backfillMap = new LinkedHashMap<>();
        backfillMap.put("start", "2024-01-01T00:00:00.000Z");
        backfillMap.put("end", "2024-06-01T00:00:00.000Z");

        Map<String, Object> v1Json = new LinkedHashMap<>();
        v1Json.put("namespace", "io.kestra.test");
        v1Json.put("flowId", "backfill-flow");
        v1Json.put("triggerId", "t1");
        v1Json.put("disabled", false);
        v1Json.put("backfill", backfillMap);

        insertV1Trigger("backfill-key", v1Json);

        // When
        migration.migrate();

        // Then
        TriggerState result = readTriggerState("backfill-key");
        assertThat(result.getBackfill()).isNotNull();
        assertThat(result.getBackfill().getStart()).isNotNull();
        assertThat(result.getBackfill().getEnd()).isNotNull();
    }

    @Test
    void shouldBeIdempotent() throws Exception {
        // Given
        Map<String, Object> v1Json = new LinkedHashMap<>();
        v1Json.put("tenantId", "idempotent-tenant");
        v1Json.put("namespace", "io.kestra.test");
        v1Json.put("flowId", "idempotent-flow");
        v1Json.put("triggerId", "t1");
        v1Json.put("date", "2024-06-15T10:30:00.000+02:00");
        v1Json.put("nextExecutionDate", "2024-06-16T10:30:00.000+02:00");
        v1Json.put("executionId", "exec-123");
        v1Json.put("workerId", "worker-1");
        v1Json.put("disabled", true);
        v1Json.put("stopAfter", List.of("FAILED"));

        insertV1Trigger("idempotent-key", v1Json);

        // When — migrate twice
        migration.migrate();
        TriggerState afterFirst = readTriggerState("idempotent-key");

        migration.migrate();
        TriggerState afterSecond = readTriggerState("idempotent-key");

        // Then — second migration must not alter the data
        assertThat(afterSecond.getEvaluatedAt()).isEqualTo(afterFirst.getEvaluatedAt());
        assertThat(afterSecond.getNextEvaluationDate()).isEqualTo(afterFirst.getNextEvaluationDate());
        assertThat(afterSecond.isLocked()).isEqualTo(afterFirst.isLocked());
        assertThat(afterSecond.isDisabled()).isEqualTo(afterFirst.isDisabled());
        assertThat(afterSecond.getWorkerId()).isEqualTo(afterFirst.getWorkerId());
        assertThat(afterSecond.getVnode()).isEqualTo(afterFirst.getVnode());
        assertThat(afterSecond.getUpdatedAt()).isEqualTo(afterFirst.getUpdatedAt());
    }

    @Test
    void shouldReturnCorrectMetadata() {
        assertThat(migration.scriptId()).isEqualTo("2.0.07-triggers");
        assertThat(migration.description()).isNotBlank();
        assertThat(migration.checksum()).isNull();
    }

    // --- Helpers ---

    private void insertV1Trigger(String key, Map<String, Object> v1Json) throws Exception {
        String json = MAPPER.writeValueAsString(v1Json);
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .insertInto(DSL.table("triggers"))
                .set(KEY_FIELD, (Object) key)
                .set(VALUE_FIELD, (Object) JSONB.valueOf(json))
                .execute()
        );
    }

    private TriggerState readTriggerState(String key) {
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
