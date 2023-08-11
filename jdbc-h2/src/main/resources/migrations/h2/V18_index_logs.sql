DROP INDEX logs_namespace;
DROP INDEX logs_timestamp;
CREATE INDEX logs_namespace_flow ON logs (deleted, timestamp, level, namespace, flow_id);