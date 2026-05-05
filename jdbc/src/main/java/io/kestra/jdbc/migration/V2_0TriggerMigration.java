package io.kestra.jdbc.migration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kestra.core.migration.MigrationScript;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.kestra.jdbc.JdbcJsonbUtils;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcRepositoryEnabled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Migrates V1 trigger rows (stored as legacy {@code Trigger} JSON) to V2 {@link TriggerState} format.
 *
 * <p>Reads all rows from the {@code triggers} table, deserializes the V1 JSON, converts each
 * to a {@link TriggerState}, and updates the row in-place. Generated columns (vnode, locked,
 * next_evaluation_epoch, etc.) are recomputed automatically by the database.
 */
@Slf4j
@Singleton
@JdbcRepositoryEnabled
public class V2_0TriggerMigration implements MigrationScript {

    private static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));

    private final JooqDSLContextWrapper dslContextWrapper;
    private final int vnodes;

    @Inject
    public V2_0TriggerMigration(
        final JooqDSLContextWrapper dslContextWrapper,
        final SchedulerConfiguration schedulerConfiguration
    ) {
        this.dslContextWrapper = dslContextWrapper;
        this.vnodes = schedulerConfiguration.vnodes();
    }

    @Override
    public String scriptId() {
        return "2.0-triggers";
    }

    @Override
    public String description() {
        return "Migrate V1 trigger rows to TriggerState";
    }

    @Override
    public String checksum() {
        return null;
    }

    @Override
    public void migrate() throws Exception {
        dslContextWrapper.transaction(configuration -> {
            int migrated = 0;
            int skipped = 0;

            try (Cursor<Record2<Object, Object>> cursor = DSL.using(configuration)
                .select(KEY_FIELD, VALUE_FIELD)
                .from(DSL.table("triggers"))
                .fetchLazy()) {

                while (cursor.hasNext()) {
                    Record2<Object, Object> row = cursor.fetchNext();
                    String key = row.get(KEY_FIELD, String.class);
                    String json = row.get(VALUE_FIELD, String.class);

                    try {
                        V1Trigger v1 = JdbcMapper.of().readValue(json, V1Trigger.class);

                        if (v1.evaluatedAt() != null) {
                            skipped++;
                            continue;
                        }

                        TriggerState state = toTriggerState(v1);
                        String newJson = JdbcMapper.of().writeValueAsString(state);

                        DSL.using(configuration)
                            .update(DSL.table("triggers"))
                            .set(VALUE_FIELD, (Object) JdbcJsonbUtils.valueOf(newJson))
                            .where(KEY_FIELD.eq(key))
                            .execute();

                        migrated++;
                    } catch (IOException e) {
                        log.error("Failed to migrate trigger with key '{}'", key, e);
                        throw new RuntimeException(e);
                    }
                }
            }

            log.info("Trigger migration complete: {} row(s) migrated, {} already in V2 format.", migrated, skipped);
        });
    }

    private TriggerState toTriggerState(V1Trigger v1) {
        return TriggerState.builder()
            .tenantId(v1.tenantId())
            .namespace(v1.namespace())
            .flowId(v1.flowId())
            .triggerId(v1.triggerId())
            .updatedAt(v1.updatedDate())
            .evaluatedAt(v1.date() != null ? v1.date().toInstant() : null)
            .nextEvaluationDate(v1.nextExecutionDate() != null ? v1.nextExecutionDate().toInstant() : null)
            .backfill(v1.backfill())
            .stopAfter(v1.stopAfter())
            .disabled(v1.disabled() != null ? v1.disabled() : false)
            .workerId(v1.workerId())
            .vnode(VNodes.computeVNodeFromTrigger(
                TriggerId.of(v1.tenantId(), v1.namespace(), v1.flowId(), v1.triggerId()),
                vnodes
            ))
            .locked(v1.executionId() != null)
            .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record V1Trigger(
        String tenantId,
        String namespace,
        String flowId,
        String triggerId,
        ZonedDateTime date,
        ZonedDateTime nextExecutionDate,
        String executionId,
        Instant updatedDate,
        String workerId,
        Backfill backfill,
        List<State.Type> stopAfter,
        Boolean disabled,
        Instant evaluatedAt
    ) {}
}
