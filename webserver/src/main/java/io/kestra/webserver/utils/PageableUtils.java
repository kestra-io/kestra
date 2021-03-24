package io.kestra.webserver.utils;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

import java.util.List;
import java.util.stream.Collectors;

public class PageableUtils {
    public static Pageable from(int page, int size, List<String> sort) throws HttpStatusException {
        return Pageable.from(
            page,
            size,
            sort(sort)
        );
    }

    public static Sort sort(List<String> sort) {
        return sort == null ? null :
            Sort.of(sort
                .stream()
                .map(s -> {
                    String[] split = s.split(":");
                    if (split.length != 2) {
                        throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid sort parameter");
                    }
                    return split[1].equals("asc") ? Sort.Order.asc(split[0]) : Sort.Order.desc(split[0]);
                })
                .collect(Collectors.toList())
            );
    }
}
