package io.kestra.core.utils;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.*;

@Singleton
public class NamespaceUtils {
    public static final String SYSTEM_FLOWS_DEFAULT_NAMESPACE = "system";

    @Getter
    @Value("${kestra.system-flows.namespace:" + "my.ns" + "}")
    private String systemFlowNamespace;

    public static List<String> asTree(String namespace) {
        List<String> split = Arrays.asList(namespace.split("\\."));
        List<String> terms = new ArrayList<>();
        for (int i = 0; i < split.size(); i++) {
            terms.add(String.join(".", split.subList(0, i + 1)));
        }

        return terms;
    }
}
