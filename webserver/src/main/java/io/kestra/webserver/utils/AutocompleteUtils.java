package io.kestra.webserver.utils;

import io.micronaut.http.exceptions.HttpStatusException;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutocompleteUtils {
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T, R> List<R> map(Function<T, R> map, List<T>... lists) throws HttpStatusException {
        Stream<T> stream = Stream.empty();

        for (List<T> list : lists) {
            stream = Stream.concat(
                stream,
                list.stream()
            );
        }

        return stream
            .distinct()
            .map(map)
            .toList();
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> List<T> from(List<T>... lists) throws HttpStatusException {
        return AutocompleteUtils
            .map(o -> o, lists);
    }
}
