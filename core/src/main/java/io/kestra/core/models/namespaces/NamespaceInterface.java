package io.kestra.core.models.namespaces;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.kestra.core.models.HasUID;

public interface NamespaceInterface extends HasUID {
    String getId();

    /**
     * Static helper method to convert a namespace string into a tree structure.
     *
     * @param namespace the namespace string to convert.
     * @return a list representing the tree structure of the namespace.
     */
    static List<String> asTree(String namespace) {
        List<String> split = Arrays.asList(namespace.split("\\."));
        List<String> terms = new ArrayList<>();
        for (int i = 0; i < split.size(); i++) {
            terms.add(String.join(".", split.subList(0, i + 1)));
        }

        return terms;
    }

    /**
     * Checks whether a namespace is the given parent or one of its descendants. The match is made on the
     * {@code .} separator, so {@code prod2} is not a descendant of {@code prod}.
     *
     * @param parent the parent namespace.
     * @param child the namespace to test.
     * @return {@code true} if {@code child} equals {@code parent} or is nested under it, {@code false} if either is {@code null}.
     */
    static boolean isDescendantOrSelf(String parent, String child) {
        return parent != null && child != null && (child.equals(parent) || child.startsWith(parent + "."));
    }

    /** {@inheritDoc **/
    @Override
    default String uid() {
        return this.getId();
    }
}
