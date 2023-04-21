package io.kestra.webserver.utils;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RequestUtils {
    private static final String LABEL_PREFIX = "label-";
    private static final Pattern LABEL_PATTERN = Pattern.compile("^" + LABEL_PREFIX + ".+$");

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

    public static Map<String, String> extractLabels(Map<String, String> inputs) {
        return inputs == null ? null : inputs.entrySet()
            .stream()
            .filter(RequestUtils::isLabel)
            .collect(Collectors.toMap(entry -> removePrefix(entry.getKey()), Map.Entry::getValue));
    }

    public static Map<String, String> extractInputs(Map<String, String> inputs) {
        return inputs == null ? null : inputs.entrySet()
            .stream()
            .filter(entry -> !isLabel(entry))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    protected static boolean isLabel(Map.Entry<String, String> entry) {
        final Matcher labelMatcher = LABEL_PATTERN.matcher(entry.getKey());
        return labelMatcher.matches();
    }

    protected static String removePrefix(String labelName) {
        if (labelName.indexOf(LABEL_PREFIX) == -1) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid label parameter");
        }
        return labelName.substring(labelName.indexOf(LABEL_PREFIX) + LABEL_PREFIX.length());
    }
}
