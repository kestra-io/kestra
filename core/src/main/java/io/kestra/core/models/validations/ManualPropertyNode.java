package io.kestra.core.models.validations;

import lombok.Getter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.ElementKind;
import javax.validation.Path;

@Getter
public class ManualPropertyNode implements Path.PropertyNode {
    private final Class<?> containerClass;
    private final String name;
    private final Integer index;
    private final Object key;
    private final ElementKind kind;
    private final boolean inIterable;

    ManualPropertyNode(
        @Nonnull String name,
        @Nullable Class<?> containerClass,
        @Nullable Integer index,
        @Nullable Object key,
        @Nonnull ElementKind kind,
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
        @Nonnull String name,
        @Nonnull ManualPropertyNode parent
    ) {
        this(name, parent.containerClass, parent.getIndex(), parent.getKey(), ElementKind.CONTAINER_ELEMENT, parent.isInIterable());
    }

    public ManualPropertyNode(
        @Nonnull String name
    ) {
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
