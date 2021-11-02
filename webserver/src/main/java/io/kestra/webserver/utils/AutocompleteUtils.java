package io.kestra.webserver.utils;

import io.micronaut.http.exceptions.HttpStatusException;
import io.kestra.core.repositories.ArrayListTotal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutocompleteUtils {
    @SafeVarargs
    public static <T> List<T> from(List<T>... lists) throws HttpStatusException {
        Stream<T> stream = Stream.empty();

        for (List<T> list : lists) {
            stream = Stream.concat(
                stream,
                list.stream()
            );
        }

        return stream
            .distinct()
            .collect(Collectors.toList());
    }
}
