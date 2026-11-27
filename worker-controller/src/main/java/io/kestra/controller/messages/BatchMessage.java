package io.kestra.controller.messages;

import io.kestra.core.utils.ListUtils;
import java.util.Arrays;
import java.util.List;

/**
 * A generic bath message.
 *
 * @param records the records of the batch.
 * @param <T> the record type.
 */
public record BatchMessage<T>(
    List<T> records) {
    public static <T> BatchMessage<T> of(List<T> records) {
        return new BatchMessage<>(records);
    }

    public static <T> BatchMessage<T> of(T... records) {
        return new BatchMessage<>(Arrays.asList(records));
    }

    public List<T> records() {
        return ListUtils.emptyOnNull(records);
    }
}
