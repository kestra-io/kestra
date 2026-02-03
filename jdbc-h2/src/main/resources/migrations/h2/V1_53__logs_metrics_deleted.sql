DROP INDEX logs_execution_id;
DROP INDEX logs_execution_id__task_id;
DROP INDEX logs_execution_id__taskrun_id;
DROP INDEX logs_namespace_flow;

ALTER table logs drop column "deleted";

CREATE INDEX IF NOT EXISTS logs_execution_id ON logs ("execution_id");
CREATE INDEX IF NOT EXISTS logs_execution_id__task_id ON logs ("execution_id", "task_id");
CREATE INDEX IF NOT EXISTS logs_execution_id__taskrun_id ON logs ("execution_id", "taskrun_id");
CREATE INDEX IF NOT EXISTS logs_namespace_flow ON logs ("tenant_id", "timestamp", "level", "namespace", "flow_id");


DROP INDEX IF EXISTS metrics_flow_id;
DROP INDEX IF EXISTS metrics_execution_id;
DROP INDEX IF EXISTS metrics_timestamp;

ALTER TABLE metrics drop column "deleted";

CREATE INDEX IF NOT EXISTS metrics_flow_id ON metrics ("tenant_id", "namespace", "flow_id");
CREATE INDEX IF NOT EXISTS metrics_execution_id ON metrics ("execution_id");
CREATE INDEX IF NOT EXISTS metrics_timestamp ON metrics ("tenant_id", "timestamp");