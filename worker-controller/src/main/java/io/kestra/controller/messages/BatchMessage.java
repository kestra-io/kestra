package io.kestra.controller.messages;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A generic bath message.
 *
 * @param records the records of the batch.
 * @param <T>     the record type.
 */
public record BatchMessage<T>(
    List<T> records
) {
    public static <T> BatchMessage<T> of(List<T> records) {
        return new BatchMessage<>(records);
    }

    public static <T> BatchMessage<T> of(T... records) {
        return new BatchMessage<>(Arrays.asList(records));
    }

    public List<T> records() {
        return Optional.ofNullable(records).orElse(List.of());
    }
}
