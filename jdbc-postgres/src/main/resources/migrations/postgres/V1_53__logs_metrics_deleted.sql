-- Indices will be re-created by the next migration
DROP INDEX logs_execution_id;
DROP INDEX logs_execution_id__task_id;
DROP INDEX logs_execution_id__taskrun_id;
DROP INDEX logs_namespace_flow;

ALTER table logs drop column "deleted";

DROP INDEX IF EXISTS metrics_flow_id;
DROP INDEX IF EXISTS metrics_execution_id;
DROP INDEX IF EXISTS metrics_timestamp;

ALTER TABLE metrics drop column "deleted";