package io.kestra.jdbc.migration;

import java.util.List;

import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

import io.kestra.jdbc.JooqDSLContextWrapper;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract integration tests for {@link V2_0_26PurgeLegacyWorkerJobRunningMigration}.
 * Subclassed per JDBC backend (H2, Postgres, MySQL).
 */
@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
public abstract class AbstractV2_0_26PurgeLegacyWorkerJobRunningMigrationTest {

    private static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));

    private static final String LEGACY_TRIGGER_JSON = """
        {
          "type": "trigger",
          "partition": 0,
          "workerInstance": {"uid": "60172c81-93fb-4ad4-b835-4792787b6e92", "workerGroup": null},
          "trigger": {"id": "watch_http", "type": "io.kestra.plugin.core.http.Trigger", "uri": "http://localhost:28080/"},
          "triggerContext": {"tenantId": "main", "namespace": "company.tmc", "flowId": "legacy_repro", "triggerId": "watch_http"},
          "conditionContext": {}
        }
        """;

    private static final String LEGACY_TASK_JSON = """
        {
          "type": "task",
          "partition": 0,
          "workerInstance": {"uid": "60172c81-93fb-4ad4-b835-4792787b6e92", "workerGroup": null},
          "task": {"id": "log", "type": "io.kestra.plugin.core.log.Log", "message": "test"},
          "taskRun": {"id": "taskrun-1", "executionId": "execution-1", "namespace": "company.tmc", "flowId": "legacy_repro", "taskId": "log"},
          "runContext": {"variables": {}}
        }
        """;

    private static final String CURRENT_TRIGGER_JSON = """
        {
          "type": "trigger",
          "workerInstance": {"uid": "60172c81-93fb-4ad4-b835-4792787b6e92", "workerQueueId": null},
          "trigger": {"id": "watch_http", "type": "io.kestra.plugin.core.http.Trigger", "uri": "http://localhost:28080/"},
          "data": {"tenantId": "main", "namespace": "company.tmc", "flowId": "legacy_repro"},
          "dispatchEpoch": 3
        }
        """;

    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    @Inject
    V2_0_26PurgeLegacyWorkerJobRunningMigration migration;

    @BeforeEach
    void cleanup() {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .deleteFrom(DSL.table("worker_job_running"))
                .execute()
        );
    }

    @Test
    void shouldPurgeEntriesWrittenByPreviousVersion() throws Exception {
        insert("legacy-trigger", LEGACY_TRIGGER_JSON);
        insert("legacy-task", LEGACY_TASK_JSON);

        migration.migrate();

        assertThat(keys()).isEmpty();
    }

    @Test
    void shouldKeepCurrentEntries() throws Exception {
        insert("current-trigger", CURRENT_TRIGGER_JSON);

        migration.migrate();

        assertThat(keys()).containsExactly("current-trigger");
    }

    @Test
    void shouldPurgeOnlyLegacyEntriesWhenBothArePresent() throws Exception {
        insert("legacy-trigger", LEGACY_TRIGGER_JSON);
        insert("current-trigger", CURRENT_TRIGGER_JSON);

        migration.migrate();

        assertThat(keys()).containsExactly("current-trigger");
    }

    @Test
    void shouldBeIdempotent() throws Exception {
        insert("legacy-trigger", LEGACY_TRIGGER_JSON);
        insert("current-trigger", CURRENT_TRIGGER_JSON);

        migration.migrate();
        migration.migrate();

        assertThat(keys()).containsExactly("current-trigger");
    }

    @Test
    void shouldKeepTheScriptIdItWasIntroducedUnder() {
        // The id is shared by every release line this migration ships on, so that an instance which
        // applied it on 2.0.x skips it on upgrade instead of recording it a second time.
        assertThat(migration.scriptId()).isEqualTo("2.0.26-purge-legacy-worker-job-running");
    }

    @Test
    void shouldDoNothingWhenTableIsEmpty() throws Exception {
        migration.migrate();

        assertThat(keys()).isEmpty();
    }

    private void insert(String key, String json) {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .insertInto(DSL.table("worker_job_running"))
                .set(KEY_FIELD, (Object) key)
                .set(VALUE_FIELD, (Object) JSONB.valueOf(json))
                .execute()
        );
    }

    private List<String> keys() {
        return dslContextWrapper.transactionResult(
            configuration -> DSL.using(configuration)
                .select(KEY_FIELD)
                .from(DSL.table("worker_job_running"))
                .fetch()
                .map(record -> record.get(KEY_FIELD, String.class))
        );
    }
}
