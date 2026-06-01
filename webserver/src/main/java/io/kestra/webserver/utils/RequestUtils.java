package io.kestra.webserver.utils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.models.flows.FlowScope;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

public class RequestUtils {
    private static final String QUERY_STRING_SEPARATOR = ":";

    /**
     * Transform colon-separated items to a {@link Map}.
     *
     * @param queryString the list of {@code key:value} parameters
     * @return the map of split pairs
     * @throws HttpStatusException when items can't be reliably split by the separator or there are duplicate keys
     */
    public static Map<String, String> toMap(List<String> queryString) {
        if (queryString == null)
            return Map.of();

        Stream<AbstractMap.SimpleEntry<String, String>> entryStream = queryString
            .stream()
            .map(s ->
            {
                String[] split = s.split(QUERY_STRING_SEPARATOR, 2);
                if (split.length < 2 || split[0] == null || split[0].isEmpty()) {
                    throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Can't split the queryString parameter by ':'");
                }

                final String key = split[0].trim();
                final String value = split[1].trim();

                if (key.isEmpty() || value.isEmpty()) {
                    throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Can't have an empty part of queryString");
                }

                if (key.matches(".*\\s.*")) {
                    throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Key of queryString can't contain a whitespace character");
                }

                return new AbstractMap.SimpleEntry<>(key, value);
            });

        try {
            return entryStream.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (IllegalStateException e) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The queryString parameter keys contains duplicities: " + e.getMessage());
        }
    }

    public static List<FlowScope> toFlowScopes(String value) {
        return Arrays.stream(value.split(","))
            .map(valueStr ->
            {
                try {
                    return FlowScope.valueOf(valueStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid FlowScope value: " + valueStr, e);
                }
            })
            .collect(Collectors.toList());
    }

}
