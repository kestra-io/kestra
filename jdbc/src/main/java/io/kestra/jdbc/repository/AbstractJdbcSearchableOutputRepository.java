package io.kestra.jdbc.repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.SQLDialect;
import org.jooq.Record;
import org.jooq.impl.DSL;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.searchable.SearchableOutput;
import io.kestra.core.searchable.SearchableOutputRepositoryInterface;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.AbstractJdbcRepository;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

/**
 * The {@code searchable_outputs} table stores one plain column per attribute so that the value can be indexed, and is keyed by
 * tenant, execution, output id and item index. Writes are an upsert on that key which only updates the value.
 */
public abstract class AbstractJdbcSearchableOutputRepository extends io.kestra.jdbc.repository.AbstractJdbcRepository implements SearchableOutputRepositoryInterface {
    public static final Field<String> EXECUTION_ID_FIELD = field("execution_id", String.class);
    public static final Field<String> OUTPUT_ID_FIELD = field("output_id", String.class);
    public static final Field<Integer> OUTPUT_INDEX_FIELD = field("output_index", Integer.class);
    public static final Field<String> VALUE_FIELD = field("value", String.class);

    private final AbstractJdbcRepository<SearchableOutput> jdbcRepository;

    public AbstractJdbcSearchableOutputRepository(AbstractJdbcRepository<SearchableOutput> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public SearchableOutput save(SearchableOutput searchableOutput) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> upsert(DSL.using(configuration), searchableOutput).execute());
        return searchableOutput;
    }

    @Override
    public int saveBatch(List<SearchableOutput> searchableOutputs) {
        if (searchableOutputs.isEmpty()) {
            return 0;
        }

        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var context = DSL.using(configuration);
                context.batch(searchableOutputs.stream().map(it -> upsert(context, it)).toList()).execute();
                return searchableOutputs.size();
            });
    }

    @Override
    public Optional<SearchableOutput> findById(String tenantId, String executionId, String outputId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select()
                .from(this.jdbcRepository.getTable())
                .where(buildTenantCondition(tenantId))
                .and(EXECUTION_ID_FIELD.eq(executionId))
                .and(OUTPUT_ID_FIELD.eq(outputId))
                .and(OUTPUT_INDEX_FIELD.eq(0))
                .limit(1)
                .fetchOptional()
                .map(AbstractJdbcSearchableOutputRepository::toSearchableOutput));
    }

    @Override
    public ArrayListTotal<SearchableOutput> findByExecutionId(String tenantId, String executionId, Pageable pageable) {
        Pageable ordered = Pageable.from(pageable.getNumber(), pageable.getSize(), Sort.of(Sort.Order.asc("output_id"), Sort.Order.asc("output_index")));
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select()
                    .from(this.jdbcRepository.getTable())
                    .where(buildTenantCondition(tenantId))
                    .and(EXECUTION_ID_FIELD.eq(executionId));

                return this.jdbcRepository.fetchPage(DSL.using(configuration), select, ordered, AbstractJdbcSearchableOutputRepository::toSearchableOutput);
            });
    }

    @Override
    public List<String> findExecutionIds(String tenantId, String outputId, QueryFilter.Op operation, Object value, int limit) {
        if (value == null) {
            throw new IllegalArgumentException("Operation %s requires a non-null value on searchable outputs.".formatted(operation));
        }

        Condition valueCondition = switch (operation) {
            case EQUALS -> VALUE_FIELD.eq(value.toString());
            case NOT_EQUALS -> VALUE_FIELD.ne(value.toString());
            case IN -> VALUE_FIELD.in(ListUtils.convertToListString(value));
            case NOT_IN -> VALUE_FIELD.notIn(ListUtils.convertToListString(value));
            default -> throw new IllegalArgumentException("Operation %s is not supported on searchable outputs.".formatted(operation));
        };

        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .selectDistinct(EXECUTION_ID_FIELD)
                .from(this.jdbcRepository.getTable())
                .where(buildTenantCondition(tenantId))
                .and(OUTPUT_ID_FIELD.eq(outputId))
                .and(valueCondition)
                .limit(limit)
                .fetch(EXECUTION_ID_FIELD));
    }

    @Override
    public int purge(List<Execution> executions) {
        List<String> executionIds = executions.stream().map(Execution::getId).toList();
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .deleteFrom(this.jdbcRepository.getTable())
                .where(EXECUTION_ID_FIELD.in(executionIds))
                .execute());
    }

    /**
     * The columns of the table key. On H2 they must be qualified by the table, which it stores in upper case: the {@code MERGE}
     * that emulates the upsert has an ambiguous join condition otherwise. Postgres only accepts unqualified ones.
     */
    private List<Field<?>> keyFields(DSLContext context) {
        String table = this.jdbcRepository.getTable().getName();
        return Stream.of("tenant_id", "execution_id", "output_id", "output_index")
            .<Field<?>>map(column -> SQLDialect.H2 == context.family() ? DSL.field(DSL.name(table.toUpperCase(Locale.ROOT), column)) : DSL.field(DSL.name(column)))
            .toList();
    }

    private Query upsert(DSLContext context, SearchableOutput searchableOutput) {
        return context
            .insertInto(this.jdbcRepository.getTable())
            .set(TENANT_ID_FIELD, searchableOutput.tenantId())
            .set(EXECUTION_ID_FIELD, searchableOutput.executionId())
            .set(OUTPUT_ID_FIELD, searchableOutput.outputId())
            .set(OUTPUT_INDEX_FIELD, searchableOutput.index())
            .set(VALUE_FIELD, searchableOutput.value())
            .onConflict(keyFields(context))
            .doUpdate()
            .set(VALUE_FIELD, DSL.excluded(VALUE_FIELD));
    }

    private static SearchableOutput toSearchableOutput(Record record) {
        return new SearchableOutput(record.get(TENANT_ID_FIELD), record.get(EXECUTION_ID_FIELD), record.get(OUTPUT_ID_FIELD), record.get(OUTPUT_INDEX_FIELD), record.get(VALUE_FIELD));
    }
}
