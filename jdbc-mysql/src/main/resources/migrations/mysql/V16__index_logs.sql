DROP INDEX ix_namespace ON logs;
DROP INDEX ix_timestamp ON logs;
CREATE INDEX ix_namespace_flow ON logs (deleted, timestamp, level, namespace, flow_id);