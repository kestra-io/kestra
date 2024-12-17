DROP INDEX ix_flow ON execution_queued;
CREATE INDEX ix_flow_date ON execution_queued (`tenant_id`, `namespace`, `flow_id`, `date`);