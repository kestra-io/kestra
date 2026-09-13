package io.kestra.jdbc.tables;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.SQLStateClass;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.fethr.table.ColumnDataType;
import io.kestra.fethr.table.ColumnDefinition;
import io.kestra.fethr.table.RowService;
import io.kestra.fethr.table.TableDefinition;
import io.kestra.fethr.table.TableRepositoryInterface;
import io.kestra.fethr.table.TableService;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Row management over JDBC.
 *
 * <p>
 * Everything here runs against a table whose shape a user chose at runtime, so two concerns recur:
 * every identifier is bound as a quoted name and every incoming value is coerced to its declared
 * column type, with a failure of either treated as bad input rather than a server fault.
 */
@Singleton
@Requires(beans = TableRepositoryInterface.class)
@Slf4j
public class JdbcRowService implements RowService {

    private static final String PK = TableDdlSupport.PK;
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private final TableService tableService;
    private final JooqDSLContextWrapper dslContextWrapper;
    private final TableDdlSupport ddl;

    public JdbcRowService(TableService tableService, JooqDSLContextWrapper dslContextWrapper, TableDdlSupport ddl) {
        this.tableService = tableService;
        this.dslContextWrapper = dslContextWrapper;
        this.ddl = ddl;
    }

    @Override
    public Map<String, Object> insertRow(String tenantId, String namespace, String name, Map<String, Object> values) {
        TableDefinition table = tableService.getTable(tenantId, namespace, name);

        // No existence check: the primary key is generated, so there is no caller-supplied identity to
        // conflict on. A unique-column violation surfaces from the database instead.
        return asBadInput(
            () -> dslContextWrapper.transactionResult(
                configuration -> doInsert(DSL.using(configuration), table, values)
            )
        );
    }

    @Override
    public ArrayListTotal<Map<String, Object>> listRows(
        String tenantId, String namespace, String name,
        String query, String sortColumn, boolean sortDescending, int page, int size) {
        TableDefinition table = tableService.getTable(tenantId, namespace, name);

        return dslContextWrapper.transactionResult(configuration ->
        {
            DSLContext ctx = DSL.using(configuration);
            return page(ctx, table, searchCondition(table, query), sortColumn, sortDescending, size, (long) (page - 1) * size);
        });
    }

    @Override
    public ArrayListTotal<Map<String, Object>> findRows(
        String tenantId, String namespace, String name,
        Map<String, Object> filter, String sortColumn, boolean sortDescending, int limit, int offset) {
        TableDefinition table = tableService.getTable(tenantId, namespace, name);

        return dslContextWrapper.transactionResult(configuration ->
        {
            DSLContext ctx = DSL.using(configuration);
            return page(ctx, table, eqCondition(table, filter), sortColumn, sortDescending, limit, offset);
        });
    }

    @Override
    public UpsertResult upsertRow(String tenantId, String namespace, String name, Map<String, Object> filter, Map<String, Object> values) {
        TableDefinition table = tableService.getTable(tenantId, namespace, name);

        return asBadInput(() -> dslContextWrapper.transactionResult(configuration ->
        {
            DSLContext ctx = DSL.using(configuration);
            Table<?> physical = DSL.table(DSL.name(table.getPhysicalTableName()));
            Field<Object> pkField = DSL.field(DSL.name(PK)).coerce(Object.class);

            // Two is enough to know the filter is ambiguous; fetching more would tell us nothing extra.
            List<Object> matched = ctx.select(pkField)
                .from(physical)
                .where(eqCondition(table, filter))
                .limit(2)
                .fetch(pkField);

            if (matched.size() > 1) {
                throw new IllegalArgumentException("Upsert filter matched more than one row in table '" + name + "'");
            }

            if (matched.isEmpty()) {
                // Filter first, values second: values win on overlap, but the inserted row still
                // satisfies the filter that missed, so re-running the same upsert finds it.
                Map<String, Object> merged = new LinkedHashMap<>(filter);
                merged.putAll(values);
                return new UpsertResult(doInsert(ctx, table, merged), true);
            }

            Map<Field<?>, Object> set = boundValues(table, values);
            if (!set.isEmpty()) {
                ctx.update(physical).set(set).where(DSL.field(DSL.name(PK)).eq(matched.getFirst())).execute();
            }

            return new UpsertResult(getByPk(ctx, table, matched.getFirst()), false);
        }));
    }

    @Override
    public Map<String, Object> updateRow(String tenantId, String namespace, String name, String rowId, Map<String, Object> values) {
        TableDefinition table = tableService.getTable(tenantId, namespace, name);
        Map<Field<?>, Object> set = boundValues(table, values);

        if (set.isEmpty()) {
            throw new IllegalArgumentException("No updatable columns in the request");
        }

        return asBadInput(() -> dslContextWrapper.transactionResult(configuration ->
        {
            DSLContext ctx = DSL.using(configuration);
            Object pk = coercePk(table, rowId);

            int updated = ctx.update(DSL.table(DSL.name(table.getPhysicalTableName())))
                .set(set)
                .where(DSL.field(DSL.name(PK)).eq(pk))
                .execute();

            if (updated == 0) {
                throw new NotFoundException("Row not found: " + rowId);
            }

            return getByPk(ctx, table, pk);
        }));
    }

    @Override
    public void deleteRows(String tenantId, String namespace, String name, List<String> rowIds) {
        TableDefinition table = tableService.getTable(tenantId, namespace, name);

        dslContextWrapper.transaction(configuration ->
        {
            DSLContext ctx = DSL.using(configuration);
            List<Object> pks = rowIds.stream().map(rowId -> coercePk(table, rowId)).toList();

            // Idempotent: an id that is not there is simply not deleted, so nothing is checked first.
            int deleted = ctx.deleteFrom(DSL.table(DSL.name(table.getPhysicalTableName())))
                .where(DSL.field(DSL.name(PK)).in(pks))
                .execute();

            if (deleted < pks.size()) {
                log.debug("Deleted {} of {} requested rows from table '{}'", deleted, pks.size(), name);
            }
        });
    }

    @Override
    public int importRows(String tenantId, String namespace, String name, List<Map<String, Object>> rows) {
        TableDefinition table = tableService.getTable(tenantId, namespace, name);

        if (rows.isEmpty()) {
            return 0;
        }

        // All-or-nothing, through the single-row path so import and insert agree on what is valid: one
        // bad value rolls the whole import back rather than leaving a partial one behind.
        return asBadInput(() -> dslContextWrapper.transactionResult(configuration ->
        {
            DSLContext ctx = DSL.using(configuration);
            rows.forEach(values -> doInsert(ctx, table, values));
            return rows.size();
        }));
    }

    // ---------------- reads ----------------

    private ArrayListTotal<Map<String, Object>> page(
        DSLContext ctx, TableDefinition table, Condition where,
        String sortColumn, boolean sortDescending, int limit, long offset) {
        Table<?> physical = DSL.table(DSL.name(table.getPhysicalTableName()));
        Field<?> order = orderField(table, sortColumn);

        long total = ctx.selectCount().from(physical).where(where).fetchOne(0, Long.class);
        List<Map<String, Object>> results = ctx.select(rowFields(table))
            .from(physical)
            .where(where)
            .orderBy(sortDescending ? order.desc() : order.asc())
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(record -> toRow(table, record));

        return new ArrayListTotal<>(results, total);
    }

    private Map<String, Object> getByPk(DSLContext ctx, TableDefinition table, Object pk) {
        return ctx.select(rowFields(table))
            .from(DSL.table(DSL.name(table.getPhysicalTableName())))
            .where(DSL.field(DSL.name(PK)).eq(pk))
            .fetchOptional()
            .map(record -> toRow(table, record))
            .orElseThrow(() -> new NotFoundException("Row not found: " + pk));
    }

    /**
     * The projection, named column by column rather than {@code select *}.
     *
     * <p>
     * Two reasons. After an {@code ALTER TABLE}, PostgreSQL rejects the driver's cached plan for an
     * unchanged {@code select *} with "cached plan must not change result type"; naming the columns
     * changes the SQL text, so a fresh plan is built. And the explicit JSON type stops H2 handing
     * the value back as raw bytes.
     */
    private List<Field<?>> rowFields(TableDefinition table) {
        List<Field<?>> fields = new ArrayList<>();
        fields.add(DSL.field(DSL.name(PK)));

        for (ColumnDefinition column : table.getColumns()) {
            fields.add(
                column.type() == ColumnDataType.JSON
                    ? DSL.field(DSL.name(column.name()), SQLDataType.JSON)
                    : DSL.field(DSL.name(column.name()))
            );
        }

        return fields;
    }

    /**
     * Free text across every non-sensitive column, so the search box can stand in for per-column
     * filters. Non-string columns are cast to text so a number, date or json value still matches.
     *
     * <p>
     * Sensitive columns are skipped: a substring match over a masked value is a way to discover it
     * one character at a time.
     */
    private Condition searchCondition(TableDefinition table, String query) {
        if (query == null || query.isBlank()) {
            return DSL.noCondition();
        }

        List<Condition> matches = new ArrayList<>();
        for (ColumnDefinition column : table.getColumns()) {
            if (column.sensitive()) {
                continue;
            }

            Field<?> field = DSL.field(DSL.name(column.name()));
            Field<String> text = switch (column.type()) {
                case STRING -> DSL.field(DSL.name(column.name()), String.class);
                // jsonb has no direct cast to varchar on PostgreSQL, so it goes through text.
                case JSON -> ddl.castAsText(field);
                default -> field.cast(String.class);
            };
            matches.add(text.containsIgnoreCase(query));
        }

        return matches.isEmpty() ? DSL.noCondition() : DSL.or(matches);
    }

    /**
     * Equality filter: each key names a column, all AND-combined, null meaning {@code IS NULL}.
     *
     * <p>
     * Sensitive columns <em>are</em> filterable here, unlike in the free-text search. Equality needs
     * the exact value already, so it is not a discovery oracle the way a substring match is -- and
     * the value still comes back masked.
     */
    private Condition eqCondition(TableDefinition table, Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return DSL.noCondition();
        }

        Set<String> columns = table.getColumns().stream().map(ColumnDefinition::name).collect(Collectors.toSet());
        List<Condition> conditions = new ArrayList<>();

        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String key = entry.getKey();
            if (!key.equals(PK) && !columns.contains(key)) {
                throw new IllegalArgumentException("Column '" + key + "' does not match the table schema");
            }

            Field<Object> field = DSL.field(DSL.name(key)).coerce(Object.class);
            Object value = entry.getValue();

            if (value == null) {
                conditions.add(field.isNull());
            } else {
                conditions.add(
                    field.eq(
                        key.equals(PK)
                            ? coercePk(table, value.toString())
                            : coerce(value, columnType(table, key))
                    )
                );
            }
        }

        return DSL.and(conditions);
    }

    private ColumnDataType columnType(TableDefinition table, String columnName) {
        return table.getColumns().stream()
            .filter(column -> column.name().equals(columnName))
            .findFirst()
            .map(ColumnDefinition::type)
            .orElseThrow(
                () -> new IllegalStateException(
                    "Column '" + columnName + "' not found on table '" + table.getName() + "'"
                )
            );
    }

    /** Sorts by a real column when one is asked for, which is also what keeps an arbitrary identifier out. */
    private Field<?> orderField(TableDefinition table, String sortColumn) {
        return sortColumn != null && !sortColumn.isBlank() && ddl.columnExists(table, sortColumn)
            ? DSL.field(DSL.name(sortColumn))
            : DSL.field(DSL.name(PK));
    }

    // ---------------- writes ----------------

    private Map<String, Object> doInsert(DSLContext ctx, TableDefinition table, Map<String, Object> values) {
        // Every key has to name a real column. A CSV header that does not is rejected rather than
        // dropped, because dropping it imports a row that is quietly missing a field.
        Set<String> columns = table.getColumns().stream().map(ColumnDefinition::name).collect(Collectors.toSet());
        for (String key : values.keySet()) {
            // A caller echoing the generated primary key back is tolerated, not an error.
            if (!key.equals(PK) && !columns.contains(key)) {
                throw new IllegalArgumentException("Column '" + key + "' does not match the table schema");
            }
        }

        Map<Field<?>, Object> set = boundValues(table, values);
        if (set.isEmpty()) {
            // Otherwise this renders "values ()" and the caller gets a SQL syntax error instead of a reason.
            throw new IllegalArgumentException("No columns to insert; the header must match the table columns");
        }

        Table<?> physical = DSL.table(DSL.name(table.getPhysicalTableName()));

        // A NUMBER primary key is a database identity; a UUID one is generated here.
        if (table.getPrimaryKeyType() == ColumnDataType.NUMBER) {
            Object generated = ctx.insertInto(physical)
                .set(set)
                .returning(DSL.field(DSL.name(PK)))
                .fetchOne()
                .get(DSL.field(DSL.name(PK)));
            return getByPk(ctx, table, generated);
        }

        UUID rowId = UUID.randomUUID();
        set.put(DSL.field(DSL.name(PK)), rowId);
        ctx.insertInto(physical).set(set).execute();

        return getByPk(ctx, table, rowId);
    }

    /** Binds only the columns the request carries, so a partial update touches only those. Never the key. */
    private Map<Field<?>, Object> boundValues(TableDefinition table, Map<String, Object> values) {
        Map<Field<?>, Object> set = new LinkedHashMap<>();

        for (ColumnDefinition column : table.getColumns()) {
            if (values.containsKey(column.name())) {
                set.put(DSL.field(DSL.name(column.name())), coerce(values.get(column.name()), column.type()));
            }
        }

        return set;
    }

    private Map<String, Object> toRow(TableDefinition table, Record record) {
        Map<String, Object> row = new LinkedHashMap<>();
        // The key column is NOT NULL, so it is always there.
        row.put(PK, record.get(DSL.field(DSL.name(PK))).toString());

        for (ColumnDefinition column : table.getColumns()) {
            Object value = record.get(DSL.field(DSL.name(column.name())));
            row.put(column.name(), column.sensitive() ? "<sensitive>" : wireValue(value));
        }

        return row;
    }

    /**
     * Coerces a submitted value to its column's type. A value that does not fit is the caller's
     * mistake on a table they defined, so it surfaces as bad input rather than an unhandled parse
     * failure.
     */
    private Object coerce(Object value, ColumnDataType type) {
        if (value == null) {
            return null;
        }

        String text = value.toString();
        try {
            return switch (type) {
                case STRING -> text;
                case NUMBER -> new BigDecimal(text);
                case BOOLEAN -> value instanceof Boolean bool ? bool : Boolean.parseBoolean(text);
                case DATETIME -> text.contains("T") && (text.endsWith("Z") || text.contains("+"))
                    ? OffsetDateTime.parse(text).toLocalDateTime()
                    : LocalDateTime.parse(text);
                case DATE -> LocalDate.parse(text);
                case UUID -> UUID.fromString(text);
                case JSON -> org.jooq.JSON.valueOf(value instanceof String ? text : writeJson(value));
            };
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid value '" + text + "' for a " + type + " column", e);
        }
    }

    private Object coercePk(TableDefinition table, String rowId) {
        try {
            return table.getPrimaryKeyType() == ColumnDataType.NUMBER
                ? Long.parseLong(rowId)
                : UUID.fromString(rowId);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid row id: " + rowId, e);
        }
    }

    private String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON value", e);
        }
    }

    /**
     * Unwraps a JSON column to its parsed value. jOOQ hands these back as its own JSON types, which
     * Jackson cannot serialize onto a response.
     */
    private Object wireValue(Object value) {
        String data;
        if (value instanceof org.jooq.JSON json) {
            data = json.data();
        } else if (value instanceof org.jooq.JSONB jsonb) {
            data = jsonb.data();
        } else {
            return value;
        }

        try {
            return data == null ? MAPPER.createObjectNode() : MAPPER.readValue(data, Object.class);
        } catch (Exception e) {
            return data;
        }
    }

    /**
     * Treats a constraint violation or an invalid value as the caller's mistake rather than a server
     * fault, since on a user-defined table it is.
     *
     * <p>
     * Classified by jOOQ's typed SQL-state class rather than by matching the raw state string, so it
     * holds across databases.
     */
    private <T> T asBadInput(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException e) {
            SQLStateClass stateClass = e.sqlStateClass();

            if (stateClass == SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION) {
                throw new IllegalArgumentException("Row violates a table constraint: " + rootMessage(e), e);
            }
            if (stateClass == SQLStateClass.C22_DATA_EXCEPTION) {
                throw new IllegalArgumentException("Invalid value for the row: " + rootMessage(e), e);
            }

            throw e;
        }
    }

    /** The deepest cause that actually says something, which is where the database's reason lives. */
    private String rootMessage(Throwable e) {
        Throwable deepest = e;
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                deepest = cause;
            }
        }
        return String.valueOf(deepest.getMessage()).strip();
    }
}
