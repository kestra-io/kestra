package io.kestra.jdbc.migration;

import java.util.ArrayList;
import java.util.List;

import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.impl.DSL;

import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.migration.MigrationScript;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Purges {@code worker_job_running} entries written by a pre-2.0 worker.
 *
 * <p>
 * The stored shape changed in 2.0: both {@code WorkerTaskRunning} and {@code WorkerTriggerRunning}
 * moved their payload into a {@code data} field ({@code runContext} and
 * {@code triggerContext}/{@code conditionContext} respectively). The 2.0 model ignores unknown
 * properties, so a 1.x entry deserializes silently with {@code data} left {@code null} and then
 * fails the first time the executor's liveness sweep tries to release it.
 *
 * <p>
 * The table holds worker leases, not durable state: every entry still present at upgrade time was
 * written by a worker of the previous version, which no longer exists. There is nothing to convert
 * — the lease is stale by definition — so the entries are dropped, which is what releasing them
 * would have done anyway.
 *
 * <p>
 * This migration only guarantees a clean starting point. A rolling upgrade can have a 1.x worker
 * still writing entries after it has run, so the executor must stay tolerant of them on its own
 * (see {@code WorkerJobRunning#isLegacy()}).
 *
 * <p>
 * The script id names the release this migration was introduced in, not the release you are running:
 * it ships on 2.0.x and on every line after it under this one id, so an instance that already applied
 * it on 2.0.x does not apply it again on upgrade. Renumbering it per branch would record the same
 * migration twice. Do not renumber it when backporting or forward-porting.
 */
@Slf4j
@Singleton
@JdbcRepositoryEnabled
public class V2_0_26PurgeLegacyWorkerJobRunningMigration implements MigrationScript {

    private static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));

    private final JooqDSLContextWrapper dslContextWrapper;

    @Inject
    public V2_0_26PurgeLegacyWorkerJobRunningMigration(final JooqDSLContextWrapper dslContextWrapper) {
        this.dslContextWrapper = dslContextWrapper;
    }

    @Override
    public String scriptId() {
        return "2.0.26-purge-legacy-worker-job-running";
    }

    @Override
    public String description() {
        return "Purge pre-2.0 worker job running entries";
    }

    @Override
    public String checksum() {
        return null;
    }

    @Override
    public void migrate() throws Exception {
        dslContextWrapper.transaction(configuration ->
        {
            // Read as a JSON tree rather than deserializing: the entry embeds a polymorphic plugin
            // type (the trigger, the task) that need not be resolvable this early in startup, and
            // deciding whether the entry is legacy only needs one field.
            List<String> legacyKeys = new ArrayList<>();

            try (
                Cursor<Record2<Object, Object>> cursor = DSL.using(configuration)
                    .select(KEY_FIELD, VALUE_FIELD)
                    .from(DSL.table("worker_job_running"))
                    .fetchLazy()
            ) {
                while (cursor.hasNext()) {
                    Record2<Object, Object> row = cursor.fetchNext();
                    String key = row.get(KEY_FIELD, String.class);
                    String json = row.get(VALUE_FIELD, String.class);

                    JsonNode node;
                    try {
                        node = JacksonMapper.ofJson().readTree(json);
                    } catch (Exception e) {
                        // Unreadable entries are stale leases too, and leaving one behind reintroduces
                        // the failure this migration exists to remove.
                        log.warn("Purging unreadable worker job running entry '{}'. Cause: {}", key, e.getMessage());
                        legacyKeys.add(key);
                        continue;
                    }

                    if (node.hasNonNull("data")) {
                        continue;
                    }

                    log.warn(
                        "Purging worker job running entry '{}' of type '{}' written by a pre-2.0 worker. {}",
                        key,
                        node.path("type").asText("unknown"),
                        resubmissionNotice(node)
                    );
                    legacyKeys.add(key);
                }
            }

            if (legacyKeys.isEmpty()) {
                log.debug("No pre-2.0 worker job running entry to purge.");
                return;
            }

            int purged = DSL.using(configuration)
                .deleteFrom(DSL.table("worker_job_running"))
                .where(KEY_FIELD.in(legacyKeys))
                .execute();

            log.info("Purged {} pre-2.0 worker job running entry(ies).", purged);
        });
    }

    /**
     * Names the work the purged entry was holding, so an operator can act on it. A task entry is the
     * only one that needs action: its task run stays where the dead worker left it and the execution
     * has to be restarted. A trigger entry needs none — the scheduler re-dispatches from the current
     * flow definition.
     */
    private static String resubmissionNotice(final JsonNode node) {
        JsonNode taskRun = node.path("taskRun");
        if (taskRun.isMissingNode()) {
            return "The scheduler will re-dispatch this trigger; no action needed.";
        }
        return "Restart execution '%s' (task '%s'): its task run cannot be resubmitted across the upgrade.".formatted(
            taskRun.path("executionId").asText("unknown"),
            taskRun.path("taskId").asText("unknown")
        );
    }
}
