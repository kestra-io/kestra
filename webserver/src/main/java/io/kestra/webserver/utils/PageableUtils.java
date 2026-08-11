package io.kestra.webserver.utils;

import java.util.List;
import java.util.function.Function;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

public class PageableUtils {
    /**
     * Maximum page size accepted on any list/search endpoint, to protect the server and
     * backend stores from accidental or abusive very-large page requests.
     */
    public static final int MAX_PAGE_SIZE = 1000;

    private PageableUtils() {
    }

    public static Pageable from(int page, int size, List<String> sort, Function<String, String> sortMapper) throws HttpStatusException {
        if (size > MAX_PAGE_SIZE) {
            throw new HttpStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "The page size must be less than or equal to %d.".formatted(MAX_PAGE_SIZE)
            );
        }

        final Pageable pageable = Pageable.from(
            page,
            size,
            sort(sort, sortMapper)
        );

        if (pageable.isUnpaged()) {
            throw new IllegalArgumentException("Unpaged data are not supported");
        }

        return pageable;
    }

    public static Pageable from(int page, int size, List<String> sort) throws HttpStatusException {
        return from(page, size, sort, null);
    }

    public static Pageable from(int page, int size) throws HttpStatusException {
        return from(page, size, null, null);
    }

    protected static Sort sort(List<String> sort, Function<String, String> sortMapper) {
        return sort == null ? null
            : Sort.of(
                sort
                    .stream()
                    .map(s ->
                    {
                        String[] split = s.split(":");
                        if (split.length != 2) {
                            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid sort parameter");
                        }
                        String col = split[0];

                        if (sortMapper != null) {
                            String mapped = sortMapper.apply(col);
                            if (mapped == null) {
                                throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid sort field: " + col);
                            }
                            col = mapped;
                        }

                        return split[1].equals("asc") ? Sort.Order.asc(col) : Sort.Order.desc(col);
                    })
                    .toList()
            );
    }
}
