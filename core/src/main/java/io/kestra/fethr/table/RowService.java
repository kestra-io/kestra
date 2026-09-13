package io.kestra.fethr.table;

import java.util.List;
import java.util.Map;

import io.kestra.core.repositories.ArrayListTotal;

/**
 * Row management for a user-defined table.
 *
 * <p>
 * Rows live only in the physical backing table, never in the registry. They are dynamic maps keyed
 * by column name plus the generated {@code _id}, and the table's own schema is what drives coercion
 * of incoming values.
 */
public interface RowService {

    Map<String, Object> insertRow(String tenantId, String namespace, String name, Map<String, Object> values);

    /**
     * A page of rows, optionally narrowed by free text across every non-sensitive column.
     */
    ArrayListTotal<Map<String, Object>> listRows(
        String tenantId, String namespace, String name,
        String query, String sortColumn, boolean sortDescending, int page, int size);

    /**
     * Equality-filtered read: each key must name a real column, all AND-combined, a null value
     * meaning {@code IS NULL}, and an empty filter matching everything.
     *
     * <p>
     * Distinct from {@link #listRows}'s free text: this answers "the row where column = value",
     * which is what the row task and the {@code tableLookup} function ask.
     */
    ArrayListTotal<Map<String, Object>> findRows(
        String tenantId, String namespace, String name,
        Map<String, Object> filter, String sortColumn, boolean sortDescending, int limit, int offset);

    /** The same read with no ordering, since a filter naming one row has no order to give. */
    default ArrayListTotal<Map<String, Object>> findRows(
        String tenantId, String namespace, String name,
        Map<String, Object> filter, int limit, int offset) {
        return findRows(tenantId, namespace, name, filter, null, false, limit, offset);
    }

    Map<String, Object> updateRow(String tenantId, String namespace, String name, String rowId, Map<String, Object> values);

    /**
     * Insert-or-update by filter rather than by primary key, because {@code _id} is generated and a
     * flow author writing an upsert rarely knows it.
     *
     * <p>
     * No match inserts the filter merged with the values, so the new row satisfies the filter that
     * missed it and a re-run finds it. One match updates it. More than one is ambiguous and throws.
     *
     * <p>
     * Without a unique constraint over the filtered columns two concurrent upserts can both insert
     * -- the same race the REST API has, not one this introduces.
     */
    UpsertResult upsertRow(String tenantId, String namespace, String name, Map<String, Object> filter, Map<String, Object> values);

    void deleteRows(String tenantId, String namespace, String name, List<String> rowIds);

    int importRows(String tenantId, String namespace, String name, List<Map<String, Object>> rows);

    record UpsertResult(Map<String, Object> row, boolean created) {
    }
}
