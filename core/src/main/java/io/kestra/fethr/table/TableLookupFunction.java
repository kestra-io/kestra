package io.kestra.fethr.table;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.runners.pebble.functions.KestraFunction;

import io.micronaut.context.annotation.Requires;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The pebble {@code tableLookup(name, filter, valueColumn, default?)} function: one column's value
 * from the single row a filter matches.
 *
 * <pre>{@code
 * {{ tableLookup('sex_code_map', {'code': trigger.hl7.PID['PID.8']}, 'label') }}
 * {{ tableLookup('sex_code_map', {'code': trigger.hl7.PID['PID.8']}, 'label', 'Unknown') }}
 * }</pre>
 *
 * <p>
 * This is the translation-table pattern -- mapping an inbound code to an outbound one -- without
 * spending a task on it.
 *
 * <p>
 * It fails rather than guesses. An empty filter, more than one match, an unknown column, or a
 * sensitive value column are all errors. The sensitive case especially: returning the masked
 * {@code <sensitive>} literal would put that string into a message as though it were the value.
 *
 * <p>
 * A miss returns {@code default} when the call passes one, and fails when it does not. Passing an
 * explicit null default is honoured and is not the same as omitting the argument.
 */
@Singleton
@Slf4j
@Requires(beans = RowService.class)
public class TableLookupFunction implements KestraFunction {

    public static final String NAME = "tableLookup";

    private static final String NAME_ARG = "name";
    private static final String FILTER_ARG = "filter";
    private static final String VALUE_COLUMN_ARG = "valueColumn";
    private static final String DEFAULT_ARG = "default";

    private static final String NAMESPACE = SystemFlowsConfiguration.DEFAULT_NAMESPACE;

    @Inject
    private RowService rowService;

    @Inject
    private TableService tableService;

    @Override
    public List<String> getArgumentNames() {
        return List.of(NAME_ARG, FILTER_ARG, VALUE_COLUMN_ARG, DEFAULT_ARG);
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put(NAME_ARG, "'my_table'");
        defaults.put(FILTER_ARG, "{'code': 'value'}");
        defaults.put(VALUE_COLUMN_ARG, "'label'");
        defaults.put(DEFAULT_ARG, null);
        return defaults;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        String name = requiredString(args, NAME_ARG, self, lineNumber);
        String valueColumn = requiredString(args, VALUE_COLUMN_ARG, self, lineNumber);

        // An empty filter matches every row, so on a one-row table the lookup would quietly return
        // it. The Rows task's FIND refuses an empty filter for the same reason.
        if (!(args.get(FILTER_ARG) instanceof Map<?, ?> map) || map.isEmpty()) {
            throw failure("The 'tableLookup' function expects a non-empty map argument 'filter'.", null, self, lineNumber);
        }
        Map<String, Object> filter = (Map<String, Object>) args.get(FILTER_ARG);

        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        String tenantId = flow.get("tenantId");

        TableDefinition table;
        try {
            table = tableService.getTable(tenantId, NAMESPACE, name);
        } catch (RuntimeException e) {
            throw failure(e.getMessage(), e, self, lineNumber);
        }

        ColumnDefinition column = table.getColumns().stream()
            .filter(candidate -> candidate.name().equalsIgnoreCase(valueColumn))
            .findFirst()
            .orElseThrow(
                () -> failure(
                    "Column '" + valueColumn + "' does not exist on table '" + name + "'.", null, self, lineNumber
                )
            );

        if (column.sensitive()) {
            throw failure(
                "Cannot look up the sensitive column '" + valueColumn + "' on table '" + name + "'.",
                null, self, lineNumber
            );
        }

        ArrayListTotal<Map<String, Object>> matches;
        try {
            matches = rowService.findRows(tenantId, NAMESPACE, name, filter, null, false, 2, 0);
        } catch (RuntimeException e) {
            throw failure(e.getMessage(), e, self, lineNumber);
        }

        if (matches.size() > 1) {
            throw failure("The 'tableLookup' filter matched more than one row in table '" + name + "'.", null, self, lineNumber);
        }

        if (matches.isEmpty()) {
            // containsKey, not a null check: an explicit null default is a real answer.
            if (args.containsKey(DEFAULT_ARG)) {
                return args.get(DEFAULT_ARG);
            }
            throw failure("No row found in table '" + name + "' for the 'tableLookup' filter.", null, self, lineNumber);
        }

        return matches.getFirst().get(column.name());
    }

    /**
     * Logs and builds the failure in one place, so a lookup that fails inside a rendered template
     * leaves a server-side trace carrying the same message the flow author sees -- and so a throw
     * added later cannot be the silent one.
     */
    private PebbleException failure(String message, Throwable cause, PebbleTemplate self, int lineNumber) {
        if (cause == null) {
            log.error(message);
        } else {
            log.error(message, cause);
        }
        return new PebbleException(cause, message, lineNumber, self.getName());
    }

    private String requiredString(Map<String, Object> args, String argName, PebbleTemplate self, int lineNumber) {
        if (!(args.get(argName) instanceof String value) || value.isEmpty()) {
            throw failure("The 'tableLookup' function expects a string argument '" + argName + "'.", null, self, lineNumber);
        }
        return value;
    }
}
