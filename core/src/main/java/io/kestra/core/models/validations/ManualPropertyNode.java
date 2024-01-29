package io.kestra.core.models.validations;

import io.micronaut.core.annotation.NonNull;
import lombok.Getter;

import io.micronaut.core.annotation.Nullable;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;

@Getter
public class ManualPropertyNode implements Path.PropertyNode {
    private final Class<?> containerClass;
    private final String name;
    private final Integer index;
    private final Object key;
    private final ElementKind kind;
    private final boolean inIterable;

    ManualPropertyNode(
        @NonNull String name,
        @Nullable Class<?> containerClass,
        @Nullable Integer index,
        @Nullable Object key,
        @NonNull ElementKind kind,
        boolean inIterable
    ) {
        this.containerClass = containerClass;
        this.name = name;
        this.index = index;
        this.key = key;
        this.kind = kind;
        this.inIterable = inIterable || index != null;
    }

    private ManualPropertyNode(
        @NonNull String name,
        @NonNull ManualPropertyNode parent
    ) {
        this(name, parent.containerClass, parent.getIndex(), parent.getKey(), ElementKind.CONTAINER_ELEMENT, parent.isInIterable());
    }

    public ManualPropertyNode(@NonNull String name) {
        this(name, null, null, null, ElementKind.PROPERTY, false);
    }

    @Override
    public Integer getTypeArgumentIndex() {
        return null;
    }

    @Override
    public <T extends Path.Node> T as(Class<T> nodeType) {
        throw new UnsupportedOperationException("Unwrapping is unsupported by this implementation");
    }
}
