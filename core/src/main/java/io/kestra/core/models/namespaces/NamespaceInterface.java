package io.kestra.core.models.namespaces;

import io.kestra.core.models.HasUID;

public interface NamespaceInterface extends HasUID {
    String getId();


    /** {@inheritDoc **/
    @Override
    default String uid() {
        return this.getId();
    }
}
