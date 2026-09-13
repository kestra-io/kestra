package io.kestra.fethr.table;

/** Index management for a user-defined table. */
public interface IndexService {

    TableDefinition createIndex(String tenantId, String namespace, String name, TableIndexDefinition index);

    TableDefinition dropIndex(String tenantId, String namespace, String name, String indexName);
}
