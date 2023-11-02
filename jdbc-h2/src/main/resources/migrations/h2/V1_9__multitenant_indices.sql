DROP INDEX IF EXISTS flows_namespace;
DROP INDEX IF EXISTS flows_namespace__id__revision;
CREATE INDEX IF NOT EXISTS flows_namespace ON flows ("deleted", "tenant_id", "namespace");
CREATE INDEX IF NOT EXISTS flows_namespace__id__revision ON flows ("deleted", "tenant_id", "namespace", "id", "revision");

DROP INDEX IF EXISTS templates_namespace;
DROP INDEX IF EXISTS templates_namespace__id;
CREATE INDEX IF NOT EXISTS templates_namespace ON templates ("deleted", "tenant_id", "namespace");
CREATE INDEX IF NOT EXISTS templates_namespace__id ON templates ("deleted", "tenant_id", "namespace", "id");

DROP INDEX IF EXISTS executions_namespace;
DROP INDEX IF EXISTS executions_flow_id;
DROP INDEX IF EXISTS executions_state_current;
DROP INDEX IF EXISTS executions_start_date;
DROP INDEX IF EXISTS executions_end_date;
DROP INDEX IF EXISTS executions_state_duration;
CREATE INDEX IF NOT EXISTS executions_namespace ON executions ("deleted", "tenant_id", "namespace");
CREATE INDEX IF NOT EXISTS executions_flow_id ON executions ("deleted", "tenant_id", "flow_id");
CREATE INDEX IF NOT EXISTS executions_state_current ON executions ("deleted", "tenant_id", "state_current");
CREATE INDEX IF NOT EXISTS executions_start_date ON executions ("deleted", "tenant_id", "start_date");
CREATE INDEX IF NOT EXISTS executions_end_date ON executions ("deleted", "tenant_id", "end_date");
CREATE INDEX IF NOT EXISTS executions_state_duration ON executions ("deleted", "tenant_id", "state_duration");

CREATE INDEX IF NOT EXISTS triggers__tenant ON triggers ("tenant_id");

DROP INDEX IF EXISTS flow_topologies_destination;
DROP INDEX IF EXISTS flow_topologies_destination__source;
CREATE INDEX IF NOT EXISTS flow_topologies_destination ON flow_topologies ("destination_tenant_id", "destination_namespace", "destination_id");
CREATE INDEX IF NOT EXISTS flow_topologies_destination__source ON flow_topologies ("destination_tenant_id", "destination_namespace", "destination_id", "source_tenant_id", "source_namespace", "source_id");

DROP INDEX IF EXISTS metrics_flow_id;
DROP INDEX IF EXISTS metrics_timestamp;
CREATE INDEX IF NOT EXISTS metrics_flow_id ON metrics ("deleted", "tenant_id", "namespace", "flow_id");
CREATE INDEX IF NOT EXISTS metrics_timestamp ON metrics ("deleted", "tenant_id", "timestamp");

DROP INDEX IF EXISTS logs_namespace_flow;
CREATE INDEX IF NOT EXISTS logs_namespace_flow ON logs ("deleted", "tenant_id", "timestamp", "level", "namespace", "flow_id");