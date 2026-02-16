ALTER TABLE logs DROP INDEX ix_namespace_flow;
ALTER TABLE logs ADD INDEX ix_tenant_timestamp (`tenant_id`, `timestamp`, `level`), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE logs ADD INDEX ix_tenant_namespace_timestamp (`tenant_id`, `namespace`, `timestamp`, `level`), ALGORITHM=INPLACE, LOCK=NONE;
