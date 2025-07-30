package io.kestra.core.storages.kv;

import java.util.List;

public record KVForNamespace(String namespace, List<KVEntry> kvEntries) {

}
