package io.kestra.core.searchable;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Data;
import io.kestra.core.models.flows.Type;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

public final class SearchableDataUtils {
    /** The size in characters of the {@code searchable_data.value} column. */
    static final int MAX_SEARCHABLE_VALUE_LENGTH = 2_048;
    static final int MAX_SEARCHABLE_ARRAY_ITEMS = 1_000;

    private SearchableDataUtils() {
        // utility class
    }

    public static List<SearchableData> toSearchableData(Logger logger, Execution execution, Data data, Object value) {
        List<String> values = toSearchableValues(logger, execution, data, value);
        return IntStream.range(0, values.size())
            .mapToObj(index -> new SearchableData(execution.getTenantId(), execution.getId(), data.getId(), index, values.get(index)))
            .toList();
    }

    /**
     * An array data is searchable item by item, any other data as a single value, and an absent data has none.
     * An array is truncated to {@value #MAX_SEARCHABLE_ARRAY_ITEMS} items and a value to {@value #MAX_SEARCHABLE_VALUE_LENGTH} characters.
     */
    private static List<String> toSearchableValues(Logger logger, Execution execution, Data data, Object value) {
        if (value == null) {
            return List.of();
        }

        if (Type.ARRAY == data.getType()) {
            if (!(value instanceof Collection<?> collection)) {
                throw new IllegalArgumentException("The value of the searchable array data '%s' is not a list.".formatted(data.getId()));
            }

            List<?> items = collection.stream().filter(Objects::nonNull).toList();
            if (items.size() > MAX_SEARCHABLE_ARRAY_ITEMS) {
                logger.warn("The searchable array data '{}' of the execution '{}' has {} items, only the first {} are searchable.",
                    data.getId(), execution.getId(), items.size(), MAX_SEARCHABLE_ARRAY_ITEMS);
                items = items.subList(0, MAX_SEARCHABLE_ARRAY_ITEMS);
            }

            List<String> values = items.stream().map(item -> toSearchableValue(data, item, true)).toList();
            long tooLong = values.stream().filter(it -> it.length() > MAX_SEARCHABLE_VALUE_LENGTH).count();
            if (tooLong > 0) {
                logger.warn("{} values of the searchable array data '{}' of the execution '{}' are longer than {} characters, they are truncated.",
                    tooLong, data.getId(), execution.getId(), MAX_SEARCHABLE_VALUE_LENGTH);
            }
            return values.stream().map(SearchableDataUtils::truncate).toList();
        }

        String single = toSearchableValue(data, value, false);
        if (single.length() > MAX_SEARCHABLE_VALUE_LENGTH) {
            logger.warn("The value of the searchable data '{}' of the execution '{}' is longer than {} characters, it is truncated.",
                data.getId(), execution.getId(), MAX_SEARCHABLE_VALUE_LENGTH);
        }
        return List.of(truncate(single));
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_SEARCHABLE_VALUE_LENGTH) {
            return value;
        }

        int end = MAX_SEARCHABLE_VALUE_LENGTH;
        // do not cut a surrogate pair in half
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String toSearchableValue(Data data, Object value, boolean arrayItem) {
        if (arrayItem && (value instanceof Collection<?> || value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Only scalar items are supported for the searchable array data '%s'.".formatted(data.getId()));
        }

        if (!Data.SEARCHABLE_TYPES.contains(data.getType())) {
            throw new IllegalArgumentException("The data '%s' cannot be searchable because its type %s is not supported.".formatted(data.getId(), data.getType()));
        }

        return value.toString();
    }
}
