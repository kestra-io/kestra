package io.kestra.core.test;

import io.kestra.core.utils.IdUtils;

public record TestSuiteUid(String tenant, String namespace, String id) {
    @Override
    public String toString() {
        return IdUtils.fromParts(tenant, namespace, id);
    }
}
