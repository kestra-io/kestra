package io.kestra.jdbc.migration;

import java.util.List;
import java.util.Set;

import org.jooq.Field;
import org.jooq.Record4;
import org.jooq.Result;
import org.jooq.Table;
import org.jooq.impl.DSL;

import io.kestra.core.migration.MigrationScript;
import io.kestra.core.migration.UnscheduledTriggerMigrationHelper;
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
 * Creates the missing {@link TriggerState} of the triggers the scheduler does not evaluate — webhook,
 * MCP-tool, flow and asset event triggers — of the flows that already exist.
 * <p>
 * Until 2.0.26 only the triggers the scheduler evaluates held a state, so the others could not be listed on
 * the triggers page. {@code FlowService} now writes their state on every flow mutation; this back-fills the
 * flows that already exist, so an upgrade does not require re-saving them.
 *
 * <p>
 * The Elasticsearch counterpart {@code V2_0_26EeUnscheduledTriggerMigration} carries the same script id and
 * does the same thing over the {@code flows} and {@code triggers} indices. The two are mutually exclusive
 * ({@link JdbcRepositoryEnabled} against {@code @ElasticSearchRepositoryEnabled}) and must be kept in sync.
 */
@Slf4j
@Singleton
@JdbcRepositoryEnabled
public class V2_0_26UnscheduledTriggerMigration implements MigrationScript {

    private static final Field<String> TENANT_FIELD = DSL.field(DSL.quotedName("tenant_id"), String.class);
    private static final Field<String> NAMESPACE_FIELD = DSL.field(DSL.quotedName("namespace"), String.class);
    private static final Field<String> ID_FIELD = DSL.field(DSL.quotedName("id"), String.class);
    private static final Field<Integer> REVISION_FIELD = DSL.field(DSL.quotedName("revision"), Integer.class);
    private static final Field<Boolean> DELETED_FIELD = DSL.field(DSL.quotedName("deleted"), Boolean.class);
    private static final Field<String> SOURCE_FIELD = DSL.field(DSL.quotedName("source_code"), String.class);

    private static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));

    private final JooqDSLContextWrapper dslContextWrapper;
    private final int vnodes;

    @Inject
    public V2_0_26UnscheduledTriggerMigration(
        final JooqDSLContextWrapper dslContextWrapper,
        final SchedulerConfiguration schedulerConfiguration) {
        this.dslContextWrapper = dslContextWrapper;
        this.vnodes = schedulerConfiguration.vnodes();
    }

    @Override
    public String scriptId() {
        return "2.0.26-unscheduled-triggers";
    }

    @Override
    public String description() {
        return "Create the missing trigger states of the webhook, MCP and flow triggers of existing flows";
    }

    @Override
    public String checksum() {
        // Java-only migration, no SQL resource file to checksum.
        return null;
    }

    @Override
    public void migrate() throws Exception {
        dslContextWrapper.transaction(configuration ->
        {
            Set<String> existingKeys = Set.copyOf(
                DSL.using(configuration).select(KEY_FIELD).from(DSL.table("triggers")).fetch(KEY_FIELD, String.class)
            );

            int created = 0;
            for (Record4<String, String, String, String> flow : latestFlows(configuration)) {
                List<TriggerState> states = UnscheduledTriggerMigrationHelper.unscheduledTriggerStates(
                    flow.get(TENANT_FIELD),
                    flow.get(NAMESPACE_FIELD),
                    flow.get(ID_FIELD),
                    flow.get(SOURCE_FIELD),
                    vnodes
                );

                for (TriggerState state : states) {
                    if (existingKeys.contains(state.uid())) {
                        continue;
                    }
                    DSL.using(configuration)
                        .insertInto(DSL.table("triggers"))
                        .set(KEY_FIELD, state.uid())
                        .set(VALUE_FIELD, JdbcJsonbUtils.valueOf(JacksonMapper.ofJson().writeValueAsString(state)))
                        .execute();
                    created++;
                }
            }

            log.info("Unscheduled trigger migration complete: {} trigger state(s) created.", created);
        });
    }

    private Result<Record4<String, String, String, String>> latestFlows(org.jooq.Configuration configuration) {
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
            .fetch();
    }
}
