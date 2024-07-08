package io.kestra.core.models.namespaces;

import io.kestra.core.models.DeletedInterface;

public interface NamespaceInterface extends DeletedInterface {
    String getId();

    default String uid() {
        return this.getId();
    }
}
