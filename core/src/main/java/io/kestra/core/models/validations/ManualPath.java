package io.kestra.core.models.validations;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;

public class ManualPath implements Path {
    final Deque<Node> nodes;

    public ManualPath(Node node) {
        this.nodes = new LinkedList<>();
        this.nodes.add(node);
    }

    @Override
    public Iterator<Node> iterator() {
        return nodes.iterator();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        final Iterator<Node> i = nodes.iterator();
        while (i.hasNext()) {
            final Node node = i.next();
            builder.append(node.getName());
            if (node.getKind() == ElementKind.CONTAINER_ELEMENT) {
                final Integer index = node.getIndex();
                if (index != null) {
                    builder.append('[').append(index).append(']');
                } else {
                    final Object key = node.getKey();
                    if (key != null) {
                        builder.append('[').append(key).append(']');
                    } else {
                        builder.append("[]");
                    }
                }

            }

            if (i.hasNext()) {
                builder.append('.');
            }

        }
        return builder.toString();
    }
}
