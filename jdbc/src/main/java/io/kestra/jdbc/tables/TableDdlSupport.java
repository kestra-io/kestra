package io.kestra.jdbc.tables;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.fethr.table.ColumnDataType;
import io.kestra.fethr.table.ColumnDefinition;
import io.kestra.fethr.table.SqlIdentifierPatterns;
import io.kestra.fethr.table.TableDefinition;
import io.kestra.fethr.table.TableIndexDefinition;
import io.kestra.jdbc.JooqDSLContextWrapper;

import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * The DDL primitives the table services share.
 *
 * <p>
 * Schema, column, index and row handling are separate services, but all of them render physical DDL,
 * map logical types to SQL ones and derive physical names. That plumbing lives here so the four
 * cannot drift.
 *
 * <p>
 * Nothing a user types reaches SQL as text. Physical names are derived, never supplied; every
 * identifier goes through {@link DSL#name(String)}; and column and index names are pattern-validated
 * before they get that far.
 */
@Singleton
@Slf4j
public class TableDdlSupport {

    public static final String PK = SqlIdentifierPatterns.PRIMARY_KEY;

    /** PostgreSQL truncates identifiers past this, so names are shortened deliberately instead. */
    private static final int MAX_PHYSICAL_LENGTH = 63;

    /** Hex characters of digest kept when a derived name has to be shortened. */
    private static final int DIGEST_LENGTH = 12;

    private final JooqDSLContextWrapper dslContextWrapper;

    public TableDdlSupport(JooqDSLContextWrapper dslContextWrapper) {
        this.dslContextWrapper = dslContextWrapper;
    }

    // ---------------- physical DDL ----------------

    public void createPhysicalTable(String physical, ColumnDataType primaryKeyType, List<ColumnDefinition> columns) {
        dslContextWrapper.transaction(configuration ->
        {
            DSLContext ctx = DSL.using(configuration);
            var create = ctx.createTable(DSL.name(physical)).column(DSL.name(PK), pkDataType(primaryKeyType));
            for (ColumnDefinition column : columns) {
                create = create.column(DSL.name(column.name()), dataType(column.type(), column.nullable()));
            }
            create.constraint(DSL.primaryKey(DSL.name(PK))).execute();
        });
    }

    public void renamePhysicalTable(String from, String to) {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration).alterTable(DSL.name(from)).renameTo(DSL.name(to)).execute()
        );
    }

    public void dropPhysicalTable(String physical) {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration).dropTableIfExists(DSL.name(physical)).execute()
        );
    }

    public void addPhysicalColumn(String physical, ColumnDefinition column) {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration).alterTable(DSL.name(physical))
                .addColumn(DSL.name(column.name()), dataType(column.type(), column.nullable()))
                .execute()
        );
    }

    public void renamePhysicalColumn(String physical, String from, String to) {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration).alterTable(DSL.name(physical))
                .renameColumn(DSL.name(from)).to(DSL.name(to))
                .execute()
        );
    }

    public void dropPhysicalColumn(String physical, String column) {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration).alterTable(DSL.name(physical)).dropColumn(DSL.name(column)).execute()
        );
    }

    /**
     * Changes a column's type, converting the values in place.
     *
     * <p>
     * PostgreSQL will not convert between unrelated types even on an empty table, so the cast is
     * explicit. jOOQ's alter-column DSL cannot render {@code USING}, hence the templated SQL -- the
     * type name comes from jOOQ rather than the caller, and both identifiers are bound as quoted
     * names, so nothing user-supplied is interpolated.
     */
    public void alterPhysicalColumnType(String physical, String column, ColumnDataType type) {
        dslContextWrapper.transaction(configuration ->
        {
            DSLContext ctx = DSL.using(configuration);
            String typeName = dataType(type, true).getTypeName(ctx.configuration());
            ctx.execute(
                "alter table {0} alter column {1} type " + typeName + " using {1}::" + typeName,
                DSL.name(physical),
                DSL.name(column)
            );
        });
    }

    public void createPhysicalIndex(String physical, TableIndexDefinition index) {
        Name[] columns = index.columns().stream().map(DSL::name).toArray(Name[]::new);
        String physicalIndex = physicalIndexName(physical, index.name());

        dslContextWrapper.transaction(configuration ->
        {
            DSLContext ctx = DSL.using(configuration);
            var step = index.unique()
                ? ctx.createUniqueIndex(DSL.name(physicalIndex))
                : ctx.createIndex(DSL.name(physicalIndex));
            step.on(DSL.name(physical), columns).execute();
        });
    }

    public void dropPhysicalIndex(String physical, String indexName) {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration).dropIndexIfExists(DSL.name(physicalIndexName(physical, indexName))).execute()
        );
    }

    private DataType<?> pkDataType(ColumnDataType type) {
        return type == ColumnDataType.NUMBER
            ? SQLDataType.BIGINT.identity(true).nullable(false)
            : SQLDataType.UUID.nullable(false);
    }

    public DataType<?> dataType(ColumnDataType type, boolean nullable) {
        DataType<?> base = switch (type) {
            case STRING -> SQLDataType.VARCHAR(4000);
            case NUMBER -> SQLDataType.NUMERIC;
            case BOOLEAN -> SQLDataType.BOOLEAN;
            case DATETIME -> SQLDataType.TIMESTAMP;
            case DATE -> SQLDataType.LOCALDATE;
            case JSON -> SQLDataType.JSON;
            case UUID -> SQLDataType.UUID;
        };
        return base.nullable(nullable);
    }

    /**
     * Casts a column to text so its value can be matched as free text.
     *
     * <p>
     * jOOQ renders CLOB as {@code cast(... as text)} on PostgreSQL, which every column type accepts
     * -- including jsonb, which cannot be cast to varchar. This is the one dialect-specific cast the
     * row search needs.
     */
    public Field<String> castAsText(Field<?> column) {
        return column.cast(SQLDataType.CLOB);
    }

    // ---------------- derived names ----------------

    /**
     * The physical table name for a logical one: tenant, namespace and name joined.
     *
     * <p>
     * When that exceeds what PostgreSQL will hold, the tail is replaced by a digest of the whole
     * rather than simply cut. Cutting is what the 1.x fork did, and it is not safe: two tables in one
     * long namespace whose names differ only past the cut would truncate to the same physical name,
     * and the second would quietly be handed the first one's rows. The digest is of the full input,
     * so distinct inputs stay distinct.
     */
    public String physicalName(String tenantId, String namespace, String name) {
        return shorten(tenantId + "_" + namespace + "_" + name);
    }

    /**
     * The physical index name, scoped by the physical table name.
     *
     * <p>
     * PostgreSQL index names are unique per schema rather than per table, so two tables each with an
     * index called {@code by_date} would otherwise collide on creation.
     */
    public String physicalIndexName(String physicalTable, String logicalIndexName) {
        return shorten(physicalTable + "_" + logicalIndexName);
    }

    /**
     * Keeps a derived name within the identifier limit, replacing the overflow with a digest of the
     * full name so shortening cannot merge two distinct names into one.
     */
    private static String shorten(String raw) {
        if (raw.length() <= MAX_PHYSICAL_LENGTH) {
            return raw;
        }

        String digest = digestOf(raw);
        return raw.substring(0, MAX_PHYSICAL_LENGTH - DIGEST_LENGTH - 1) + "_" + digest;
    }

    private static String digestOf(String raw) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, DIGEST_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ---------------- validation and lookups ----------------

    public List<ColumnDefinition> normalizeColumns(List<ColumnDefinition> columns) {
        List<ColumnDefinition> out = new ArrayList<>();
        int position = 0;
        for (ColumnDefinition column : columns) {
            validateColumnName(column.name());
            out.add(withPosition(column, position++));
        }
        return out;
    }

    /** Re-numbers columns after one is added, removed or moved, so position stays the list order. */
    public List<ColumnDefinition> renumber(List<ColumnDefinition> columns) {
        List<ColumnDefinition> out = new ArrayList<>();
        int position = 0;
        for (ColumnDefinition column : columns) {
            out.add(withPosition(column, position++));
        }
        return out;
    }

    private static ColumnDefinition withPosition(ColumnDefinition column, int position) {
        return new ColumnDefinition(
            column.name(),
            column.type(),
            column.nullable(),
            column.sensitive(),
            position,
            column.hint(),
            column.defaultValue()
        );
    }

    public void validateColumnName(String name) {
        if (name == null || !SqlIdentifierPatterns.COLUMN_IDENTIFIER.matcher(name).matches() || name.equals(PK)) {
            throw new IllegalArgumentException("Invalid column name: " + name);
        }
    }

    public void validateIndexName(String name) {
        if (name == null || !SqlIdentifierPatterns.COLUMN_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid index name: " + name);
        }
    }

    /** A duplicate table, column or index name is a constraint violation, which maps to a 422. */
    public ConstraintViolationException nameConflict(String message, String propertyPath, String value) {
        return new ConstraintViolationException(
            Set.of(
                ManualConstraintViolation.of(message, value, String.class, propertyPath, value)
            )
        );
    }

    public boolean columnExists(TableDefinition table, String columnName) {
        return table.getColumns().stream().anyMatch(column -> column.name().equals(columnName));
    }

    public ColumnDefinition findColumn(TableDefinition table, String columnName) {
        return table.getColumns().stream()
            .filter(column -> column.name().equals(columnName))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Column not found: " + columnName));
    }
}
