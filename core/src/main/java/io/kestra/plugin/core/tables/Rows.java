package io.kestra.plugin.core.tables;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.runners.RunContext;
import io.kestra.fethr.plugin.AppliesWhen;
import io.kestra.fethr.table.ColumnReference;
import io.kestra.fethr.table.RowService;
import io.kestra.fethr.table.TableReference;
import io.kestra.fethr.table.validation.TableRowsValidation;
import io.kestra.fethr.taxonomy.Category;
import io.kestra.fethr.taxonomy.FethrTaxonomy;
import io.kestra.fethr.taxonomy.SubCategory;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
@TableRowsValidation
@Schema(
    title = "Table Rows",
    description = "Read or write rows of a user-defined table; `action` picks the operation and only the " +
        "fields it needs apply. `INSERT` adds a row from `data`. `UPDATE` patches the single row matched by " +
        "`filter` with `patch`; no match fails, and more than one match fails -- narrow the filter, or filter " +
        "on `_id` to target one row by id. `UPSERT` matches by `filter`: no match inserts a new row (the filter columns are set " +
        "first, `data` applied on top); exactly one match updates that row; more than one match fails -- " +
        "narrow the filter or use action `QUERY`. Without a unique constraint on the filtered columns, two " +
        "concurrent upserts with the same filter can both insert. `QUERY` reads every row matching `filter` " +
        "(an empty filter returns every row), paginated by `limit`/`offset`; to read a single row use action " +
        "`FIND` instead. `FIND` returns a single row by `filter`, and fails if more than one row matches -- " +
        "use action `QUERY` for multi-row reads. A common `FIND` use is translation-table lookups, e.g. " +
        "mapping an inbound HL7 code to an outbound one."
)
@Plugin(
    examples = {
        @Example(
            title = "Insert a customer row into the `customers` table.",
            full = true,
            code = """
                id: tables_insert
                namespace: company.team

                tasks:
                  - id: insert_customer
                    type: io.kestra.plugin.core.tables.Rows
                    action: INSERT
                    tableName: customers
                    data:
                      name: Jane Doe
                      email: jane.doe@example.com
                """
        ),
        @Example(
            title = "Update the single customer matching an email.",
            full = true,
            code = """
                id: tables_update
                namespace: company.team

                tasks:
                  - id: update_customer
                    type: io.kestra.plugin.core.tables.Rows
                    action: UPDATE
                    tableName: customers
                    filter:
                      email: jane.doe@example.com
                    patch:
                      status: inactive
                """
        ),
        @Example(
            title = "Update a row inserted earlier in the flow, targeting it by its `_id`.",
            full = true,
            code = """
                id: tables_update_by_id
                namespace: company.team

                tasks:
                  - id: insert_customer
                    type: io.kestra.plugin.core.tables.Rows
                    action: INSERT
                    tableName: customers
                    data:
                      name: Jane Doe
                      email: jane.doe@example.com

                  - id: update_customer
                    type: io.kestra.plugin.core.tables.Rows
                    action: UPDATE
                    tableName: customers
                    filter:
                      _id: "{{ outputs.insert_customer.id }}"
                    patch:
                      email: jane.new@example.com
                """
        ),
        @Example(
            title = "Keep a customer row in sync by email, without knowing its row id.",
            full = true,
            code = """
                id: tables_upsert
                namespace: company.team

                tasks:
                  - id: sync_customer
                    type: io.kestra.plugin.core.tables.Rows
                    action: UPSERT
                    tableName: customers
                    filter:
                      email: jane.doe@example.com
                    data:
                      name: Jane Doe
                      status: active
                """
        ),
        @Example(
            title = "List every active customer, most recently inserted first.",
            full = true,
            code = """
                id: tables_query
                namespace: company.team

                tasks:
                  - id: active_customers
                    type: io.kestra.plugin.core.tables.Rows
                    action: QUERY
                    tableName: customers
                    filter:
                      status: active
                    sortColumn: _id
                    sortDescending: true
                    limit: 100
                """
        ),
        @Example(
            title = "Translate an inbound HL7 PID-8 sex code to the outbound code, via a `sex_code_map` table.",
            full = true,
            code = """
                id: pid8_translate
                namespace: company.team

                tasks:
                  - id: translate_sex_code
                    type: io.kestra.plugin.core.tables.Rows
                    action: FIND
                    tableName: sex_code_map
                    filter:
                      source: "{{ trigger.hl7.PID['PID.8'] }}"
                """
        )
    },
    aliases = {
        "add row", "insert row", "append row", "new row",
        "update row", "edit row", "modify row", "patch row",
        "insert or update", "merge row", "sync row", "upsert row",
        "select rows", "search table", "list rows", "read rows",
        "find row", "get row", "lookup row", "translate"
    }
)
@FethrTaxonomy(
    category = Category.CORE,
    subCategory = SubCategory.TABLES,
    icon = "Table",
    order = 1
)
public class Rows extends Task implements RunnableTask<Rows.Output> {
    @NotNull
    @Schema(
        title = "Action",
        description = "The row operation to perform."
    )
    private Action action;

    @NotNull
    @TableReference
    @Schema(
        title = "The name of the table"
    )
    private Property<String> tableName;

    @AppliesWhen(property = "action", values = { "INSERT", "UPSERT" }, requiredFor = { "INSERT", "UPSERT" })
    @ColumnReference(property = "tableName")
    @PluginProperty(additionalProperties = String.class)
    @Schema(
        title = "The row payload",
        description = "A map of column name to value; every key must match a real column on the table. For " +
            "`action: INSERT` this is the whole row. For `action: UPSERT` it is applied on top of the " +
            "`filter` columns when inserting, or as the patch when updating."
    )
    private Property<Map<String, Object>> data;

    @AppliesWhen(property = "action", values = { "UPDATE" }, requiredFor = { "UPDATE" })
    @ColumnReference(property = "tableName")
    @PluginProperty(additionalProperties = String.class)
    @Schema(
        title = "The columns to patch",
        description = "A map of column name to new value. Only the columns present here are changed."
    )
    private Property<Map<String, Object>> patch;

    @AppliesWhen(property = "action", values = { "UPDATE", "UPSERT", "QUERY", "FIND" }, requiredFor = { "UPDATE", "UPSERT", "FIND" })
    @ColumnReference(property = "tableName")
    @PluginProperty(additionalProperties = String.class)
    @Schema(
        title = "The match filter",
        description = "A map of column name to value; every key must match a real column on the table (`_id` " +
            "is also accepted, to target a row by id -- e.g. from a previous task's output), and a row " +
            "matches when every column equals its filter value. For `action: QUERY`, leave unset (or empty) " +
            "to match every row; for `UPDATE`, `UPSERT` and `FIND` it is required, and for `UPDATE` it must " +
            "match exactly one row."
    )
    private Property<Map<String, Object>> filter;

    @AppliesWhen(property = "action", values = { "QUERY" })
    @ColumnReference(property = "tableName")
    @Schema(
        title = "The column to sort by",
        description = "Defaults to the row id (`_id`, insertion order) when unset or not a real column."
    )
    private Property<String> sortColumn;

    @AppliesWhen(property = "action", values = { "QUERY" })
    @NotNull
    @Schema(
        title = "Sort in descending order"
    )
    @Builder.Default
    private Property<Boolean> sortDescending = Property.ofValue(false);

    @AppliesWhen(property = "action", values = { "QUERY" })
    @NotNull
    @Schema(
        title = "The maximum number of rows to return",
        description = "Capped at 1000."
    )
    @Builder.Default
    private Property<Integer> limit = Property.ofValue(100);

    @AppliesWhen(property = "action", values = { "QUERY" })
    @NotNull
    @Schema(
        title = "The number of rows to skip"
    )
    @Builder.Default
    private Property<Integer> offset = Property.ofValue(0);

    @AppliesWhen(property = "action", values = { "FIND" })
    @NotNull
    @Schema(
        title = "Fail when no row matches",
        description = "If `false` (the default), a miss returns `found: false` instead of failing the task."
    )
    @Builder.Default
    private Property<Boolean> errorOnMissing = Property.ofValue(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        RowService rowService = TablesTaskSupport.rowService(runContext);
        String table = runContext.render(this.tableName).as(String.class).orElseThrow();
        String tenantId = TablesTaskSupport.tenantId(runContext);
        String namespace = TablesTaskSupport.namespace();

        return switch (this.action) {
            case INSERT -> insert(runContext, rowService, tenantId, namespace, table);
            case UPDATE -> update(runContext, rowService, tenantId, namespace, table);
            case UPSERT -> upsert(runContext, rowService, tenantId, namespace, table);
            case QUERY -> query(runContext, rowService, tenantId, namespace, table);
            case FIND -> find(runContext, rowService, tenantId, namespace, table);
        };
    }

    private Output insert(RunContext runContext, RowService rowService, String tenantId, String namespace, String table) throws Exception {
        Map<String, Object> renderedData = runContext.render(require(this.data, "data")).asMap(String.class, Object.class);

        Map<String, Object> row = rowService.insertRow(tenantId, namespace, table, renderedData);

        return Output.builder()
            .id(String.valueOf(row.get("_id")))
            .row(row)
            .build();
    }

    private Output update(RunContext runContext, RowService rowService, String tenantId, String namespace, String table) throws Exception {
        Map<String, Object> renderedFilter = requireNonEmpty(
            runContext.render(require(this.filter, "filter")).asMap(String.class, Object.class), "filter"
        );
        Map<String, Object> renderedPatch = runContext.render(require(this.patch, "patch")).asMap(String.class, Object.class);

        ArrayListTotal<Map<String, Object>> matches = rowService.findRows(
            tenantId, namespace, table, renderedFilter, null, false, 2, 0
        );

        if (matches.size() > 1) {
            throw new IllegalArgumentException("Filter matched more than one row in table '" + table + "'; narrow the filter to exactly the row to update");
        }

        if (matches.isEmpty()) {
            throw new NoSuchElementException("No row found in table '" + table + "' for filter " + renderedFilter);
        }

        // Find-then-update is not atomic: a row deleted between the two calls surfaces as the service's
        // not-found error -- the same class of race the class doc already accepts for concurrent UPSERTs.
        Map<String, Object> row = rowService.updateRow(
            tenantId, namespace, table, String.valueOf(matches.getFirst().get("_id")), renderedPatch
        );

        return Output.builder()
            .id(String.valueOf(row.get("_id")))
            .row(row)
            .build();
    }

    private Output upsert(RunContext runContext, RowService rowService, String tenantId, String namespace, String table) throws Exception {
        Map<String, Object> renderedFilter = requireNonEmpty(
            runContext.render(require(this.filter, "filter")).asMap(String.class, Object.class), "filter"
        );
        Map<String, Object> renderedData = runContext.render(require(this.data, "data")).asMap(String.class, Object.class);

        RowService.UpsertResult result = rowService.upsertRow(tenantId, namespace, table, renderedFilter, renderedData);

        return Output.builder()
            .id(String.valueOf(result.row().get("_id")))
            .row(result.row())
            .created(result.created())
            .build();
    }

    private Output query(RunContext runContext, RowService rowService, String tenantId, String namespace, String table) throws Exception {
        Map<String, Object> renderedFilter = runContext.render(this.filter).asMap(String.class, Object.class);
        String renderedSortColumn = runContext.render(this.sortColumn).as(String.class).orElse(null);
        boolean renderedSortDescending = runContext.render(this.sortDescending).as(Boolean.class).orElseThrow();
        int renderedLimit = Math.min(runContext.render(this.limit).as(Integer.class).orElseThrow(), 1000);
        int renderedOffset = runContext.render(this.offset).as(Integer.class).orElseThrow();

        ArrayListTotal<Map<String, Object>> rows = rowService.findRows(
            tenantId, namespace, table, renderedFilter, renderedSortColumn, renderedSortDescending, renderedLimit, renderedOffset
        );

        return Output.builder()
            .rows(rows)
            .count(rows.size())
            .total(rows.getTotal())
            .build();
    }

    private Output find(RunContext runContext, RowService rowService, String tenantId, String namespace, String table) throws Exception {
        Map<String, Object> renderedFilter = requireNonEmpty(
            runContext.render(require(this.filter, "filter")).asMap(String.class, Object.class), "filter"
        );
        boolean renderedErrorOnMissing = runContext.render(this.errorOnMissing).as(Boolean.class).orElseThrow();

        ArrayListTotal<Map<String, Object>> matches = rowService.findRows(
            tenantId, namespace, table, renderedFilter, null, false, 2, 0
        );

        if (matches.size() > 1) {
            throw new IllegalArgumentException("Filter matched more than one row in table '" + table + "'; use action QUERY");
        }

        if (matches.isEmpty()) {
            if (renderedErrorOnMissing) {
                throw new NoSuchElementException("No row found in table '" + table + "' for filter " + renderedFilter);
            }
            return Output.builder().found(false).build();
        }

        Map<String, Object> row = matches.getFirst();
        return Output.builder()
            .found(true)
            .id(String.valueOf(row.get("_id")))
            .row(row)
            .build();
    }

    /**
     * A {@link Property} left unset on an action that needs it renders as an empty value rather than
     * failing (an unset {@code Property<Map>} renders to an empty map, see {@link #requireNonEmpty}) -- this
     * is the last line of defense for a task saved before {@link TableRowsValidation} existed, or edited
     * through the API without going through flow validation.
     */
    private <T> Property<T> require(Property<T> value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("`" + name + "` is required when `action` is " + this.action);
        }
        return value;
    }

    /**
     * An unset {@code filter} renders to an empty map rather than failing (see {@link #require}); on
     * `UPDATE` and `UPSERT` an empty filter would match up to two arbitrary rows instead of narrowing to
     * one, and on `FIND` it would read as "no columns to match" instead of the missing filter it actually is.
     */
    private Map<String, Object> requireNonEmpty(Map<String, Object> rendered, String name) {
        if (rendered.isEmpty()) {
            throw new IllegalArgumentException("`" + name + "` cannot be empty when `action` is " + this.action);
        }
        return rendered;
    }

    public enum Action {
        INSERT,
        UPDATE,
        UPSERT,
        QUERY,
        FIND
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The row id (`_id`)",
            description = "Set for `INSERT`, `UPDATE`, `UPSERT`, and a matched `FIND`."
        )
        private final String id;

        @Schema(
            title = "The row",
            description = "Set for `INSERT`, `UPDATE`, `UPSERT`, and a matched `FIND`. Sensitive columns are masked."
        )
        private final Map<String, Object> row;

        @Schema(
            title = "Whether a new row was inserted",
            description = "Set for `UPSERT` only: `true` if the filter matched no row and a new one was " +
                "inserted, `false` if an existing row was updated."
        )
        private final Boolean created;

        @Schema(
            title = "The matched rows",
            description = "Set for `QUERY` only. Sensitive columns are masked."
        )
        private final List<Map<String, Object>> rows;

        @Schema(
            title = "The number of rows returned",
            description = "Set for `QUERY` only -- the size of `rows`, not the total match count, see `total`."
        )
        private final Integer count;

        @Schema(
            title = "The total number of rows matching `filter`",
            description = "Set for `QUERY` only, across every page, not just this one."
        )
        private final Long total;

        @Schema(
            title = "Whether a row matched",
            description = "Set for `FIND` only."
        )
        private final Boolean found;
    }
}
