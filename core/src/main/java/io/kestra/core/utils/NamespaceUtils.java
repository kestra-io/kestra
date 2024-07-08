package io.kestra.core.utils;

import java.util.*;

public abstract class NamespaceUtils {
    public static List<String> asTree(String namespace) {
        List<String> split = Arrays.asList(namespace.split("\\."));
        List<String> terms = new ArrayList<>();
        for (int i = 0; i < split.size(); i++) {
            terms.add(String.join(".", split.subList(0, i + 1)));
        }

        return terms;
    }
}
