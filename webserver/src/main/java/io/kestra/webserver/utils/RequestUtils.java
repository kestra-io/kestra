package io.kestra.webserver.utils;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RequestUtils {
    public static Map<String, String> toMap(List<String> queryString) {
        return queryString == null ? null : queryString
            .stream()
            .map(s -> {
                String[] split = s.split(":");
                if (split.length != 2) {
                    throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid queryString parameter");
                }

                return new AbstractMap.SimpleEntry<>(
                    split[0],
                    split[1]
                );
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
