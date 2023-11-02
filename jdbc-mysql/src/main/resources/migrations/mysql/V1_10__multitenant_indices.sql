DROP INDEX ix_namespace ON flows;
DROP INDEX ix_namespace__id__revision ON flows;
CREATE INDEX ix_namespace ON flows (`deleted`, `tenant_id`, `namespace`);
CREATE INDEX ix_namespace__id__revision ON flows (`deleted`, `tenant_id`, `namespace`, `id`, `revision`);

DROP INDEX ix_namespace ON templates;
DROP INDEX ix_namespace__id ON templates;
CREATE INDEX ix_namespace ON templates (`deleted`, `tenant_id`, `namespace`);
CREATE INDEX ix_namespace__id ON templates (`deleted`, `tenant_id`, `namespace`, `id`);

DROP INDEX ix_namespace ON executions;
DROP INDEX ix_flowId ON executions;
DROP INDEX ix_state_current ON executions;
DROP INDEX ix_start_date ON executions;
DROP INDEX ix_end_date ON executions;
DROP INDEX ix_state_duration ON executions;
CREATE INDEX ix_namespace ON executions (`deleted`, `tenant_id`, `namespace`);
CREATE INDEX ix_flowId ON executions (`deleted`, `tenant_id`, `flow_id`);
CREATE INDEX ix_state_current ON executions (`deleted`, `tenant_id`, `state_current`);
CREATE INDEX ix_start_date ON executions (`deleted`, `tenant_id`, `start_date`);
CREATE INDEX ix_end_date ON executions (`deleted`, `tenant_id`, `end_date`);
CREATE INDEX ix_state_duration ON executions (`deleted`, `tenant_id`, `state_duration`);

CREATE INDEX ix_tenant_id ON triggers (`tenant_id`);

DROP INDEX ix_destination ON flow_topologies;
DROP INDEX ix_destination__source ON flow_topologies;
CREATE INDEX ix_destination ON flow_topologies (`destination_tenant_id`, `destination_namespace`, `destination_id`);
CREATE INDEX ix_source ON flow_topologies (`source_tenant_id`, `source_namespace`, `source_id`);

DROP INDEX ix_metrics_flow_id ON metrics;
DROP INDEX ix_metrics_timestamp ON metrics;
CREATE INDEX metrics_flow_id ON metrics (`deleted`, `tenant_id`, `namespace`, `flow_id`);
CREATE INDEX metrics_timestamp ON metrics (`deleted`, `tenant_id`, `timestamp`);

DROP INDEX ix_namespace_flow ON logs;
CREATE INDEX ix_namespace_flow ON logs (`deleted`, `tenant_id`, `timestamp`, `level`, `namespace`, `flow_id`);