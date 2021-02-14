package org.kestra.webserver.utils;

import io.micronaut.http.exceptions.HttpStatusException;
import org.kestra.core.repositories.ArrayListTotal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutocompleteUtils {
    public static <T> List<T> from(List<T> ids, ArrayList<T> search) throws HttpStatusException {
        return Stream
            .concat(
                ids.stream(),
                search.stream()
            )
            .distinct()
            .collect(Collectors.toList());
    }
}
