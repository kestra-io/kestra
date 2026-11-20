package io.kestra.core.models.namespaces;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.kestra.core.models.HasUID;

public interface NamespaceInterface extends HasUID {
    /**
     * Pattern of a valid namespace id: dot-separated segments, none of them empty, since each dot becomes a
     * storage path separator.
     */
    String ID_PATTERN = "^[a-z0-9][a-z0-9_-]*(\\.[a-z0-9_-]+)*";

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

    /** {@inheritDoc **/
    @Override
    default String uid() {
        return this.getId();
    }
}
