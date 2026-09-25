package io.kestra.jdbc.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.Configuration;
import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Record4;
import org.jooq.Table;
import org.jooq.impl.DSL;

import io.kestra.core.migration.AbstractV2_1_04UnscheduledTriggerMigration;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.jdbc.JdbcJsonbUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The JDBC half of the {@code 2.1.04-unscheduled-triggers} back-fill, over the {@code flows} and
 * {@code triggers} tables. What it creates, and for which triggers, is decided by
 * {@link AbstractV2_1_04UnscheduledTriggerMigration}; this class only reads and writes.
 *
 * <p>
 * Flows are streamed through a cursor and their states inserted in batches rather than read and written one
 * row at a time: this runs at startup against every flow an instance holds, and a flow source is large enough
 * that materialising all of them at once is a real memory cost on a big instance.
 */
@Slf4j
@Singleton
@JdbcRepositoryEnabled
public class V2_1_04UnscheduledTriggerMigration extends AbstractV2_1_04UnscheduledTriggerMigration {

    /**
     * Rows pulled from the driver at a time. Without it the PostgreSQL driver buffers the whole result set,
     * which is exactly what streaming the flows is meant to avoid.
     */
    private static final int FETCH_SIZE = 100;

    /**
     * States accumulated before a round-trip. Bounds both the existence check and the insert batch.
     */
    private static final int BATCH_SIZE = 500;

    private static final Field<String> TENANT_FIELD = DSL.field(DSL.quotedName("tenant_id"), String.class);
    private static final Field<String> NAMESPACE_FIELD = DSL.field(DSL.quotedName("namespace"), String.class);
    private static final Field<String> ID_FIELD = DSL.field(DSL.quotedName("id"), String.class);
    private static final Field<Integer> REVISION_FIELD = DSL.field(DSL.quotedName("revision"), Integer.class);
    private static final Field<Boolean> DELETED_FIELD = DSL.field(DSL.quotedName("deleted"), Boolean.class);
    private static final Field<String> SOURCE_FIELD = DSL.field(DSL.quotedName("source_code"), String.class);

    private static final Field<String> KEY_FIELD = DSL.field(DSL.quotedName("key"), String.class);
    private static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));

    private final JooqDSLContextWrapper dslContextWrapper;

    @Inject
    public V2_1_04UnscheduledTriggerMigration(
        final JooqDSLContextWrapper dslContextWrapper,
        final SchedulerConfiguration schedulerConfiguration) {
        super(schedulerConfiguration);
        this.dslContextWrapper = dslContextWrapper;
    }

    @Override
    public void migrate() throws Exception {
        dslContextWrapper.transaction(configuration ->
        {
            int created = 0;
            List<TriggerState> pending = new ArrayList<>();

            try (Cursor<Record4<String, String, String, String>> cursor = latestFlows(configuration)) {
                while (cursor.hasNext()) {
                    Record4<String, String, String, String> flow = cursor.fetchNext();

                    pending.addAll(
                        unscheduledTriggerStates(
                            flow.get(TENANT_FIELD),
                            flow.get(NAMESPACE_FIELD),
                            flow.get(ID_FIELD),
                            flow.get(SOURCE_FIELD)
                        )
                    );

                    if (pending.size() >= BATCH_SIZE) {
                        created += insertMissing(configuration, pending);
                        pending.clear();
                    }
                }
            }

            created += insertMissing(configuration, pending);

            log.info("Unscheduled trigger migration complete: {} trigger state(s) created.", created);
        });
    }

    /**
     * Inserts the states of the batch that do not already exist, and returns how many were created.
     * <p>
     * The existence check is scoped to the batch rather than taken once over the whole table: the migration is
     * re-runnable after a failure, so it has to skip what a previous attempt created, but it must not hold
     * every trigger key of the instance in memory to do it.
     */
    private int insertMissing(Configuration configuration, List<TriggerState> states) throws Exception {
        if (states.isEmpty()) {
            return 0;
        }

        Set<String> uids = states.stream().map(TriggerState::uid).collect(Collectors.toSet());
        Set<String> existing = Set.copyOf(
            DSL.using(configuration)
                .select(KEY_FIELD)
                .from(DSL.table("triggers"))
                .where(KEY_FIELD.in(uids))
                .fetch(KEY_FIELD)
        );

        List<Query> inserts = new ArrayList<>();
        for (TriggerState state : states) {
            if (existing.contains(state.uid())) {
                continue;
            }
            inserts.add(
                DSL.using(configuration)
                    .insertInto(DSL.table("triggers"))
                    .set(KEY_FIELD, state.uid())
                    .set(VALUE_FIELD, JdbcJsonbUtils.valueOf(JacksonMapper.ofJson().writeValueAsString(state)))
            );
        }

        if (inserts.isEmpty()) {
            return 0;
        }

        DSL.using(configuration).batch(inserts).execute();
        return inserts.size();
    }

    private Cursor<Record4<String, String, String, String>> latestFlows(Configuration configuration) {
        // Unquoted on purpose: H2 folds the unquoted DDL name to upper case, so a quoted
        // lower-case reference would not resolve (same as the other flow-crawling migrations).
        Table<?> flows = DSL.table("flows");
        Table<?> latest = DSL.select(
            TENANT_FIELD.as("latest_tenant_id"),
            NAMESPACE_FIELD.as("latest_namespace"),
            ID_FIELD.as("latest_id"),
            DSL.max(REVISION_FIELD).as("latest_revision")
        )
            .from(flows)
            .groupBy(TENANT_FIELD, NAMESPACE_FIELD, ID_FIELD)
            .asTable("latest");

        return DSL.using(configuration)
            .select(TENANT_FIELD, NAMESPACE_FIELD, ID_FIELD, SOURCE_FIELD)
            .from(flows)
            .join(latest)
            .on(
                TENANT_FIELD.isNotDistinctFrom(latest.field("latest_tenant_id", String.class)),
                NAMESPACE_FIELD.eq(latest.field("latest_namespace", String.class)),
                ID_FIELD.eq(latest.field("latest_id", String.class)),
                REVISION_FIELD.eq(latest.field("latest_revision", Integer.class))
            )
            .where(DELETED_FIELD.isFalse())
            .fetchSize(FETCH_SIZE)
            .fetchLazy();
    }
}
