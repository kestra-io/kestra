ALTER TABLE logs DROP INDEX ix_execution_id;
ALTER TABLE logs DROP INDEX ix_execution_id__task_id;
ALTER TABLE logs DROP INDEX ix_execution_id__taskrun_id;
ALTER TABLE logs DROP INDEX ix_namespace_flow;

ALTER table logs drop column `deleted`;

ALTER TABLE logs ADD INDEX ix_execution_id (`execution_id`), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE logs ADD INDEX ix_execution_id__task_id (`execution_id`, `task_id`), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE logs ADD INDEX ix_execution_id__taskrun_id (`execution_id`, `taskrun_id`), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE logs ADD INDEX ix_namespace_flow (`tenant_id`, `timestamp`, `level`, `namespace`, `flow_id`), ALGORITHM=INPLACE, LOCK=NONE;


ALTER TABLE metrics DROP INDEX metrics_flow_id;
ALTER TABLE metrics DROP INDEX ix_metrics_execution_id;
ALTER TABLE metrics DROP INDEX metrics_timestamp;

ALTER TABLE metrics drop column `deleted`;

ALTER TABLE metrics ADD INDEX ix_metrics_flow_id (`tenant_id`, `namespace`, `flow_id`), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE metrics ADD INDEX ix_metrics_execution_id (`execution_id`), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE metrics ADD INDEX ix_metrics_timestamp (`tenant_id`, `timestamp`), ALGORITHM=INPLACE, LOCK=NONE;