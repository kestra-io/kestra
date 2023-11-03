alter table multipleconditions add `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED;

DROP INDEX ix_namespace__flow_id__condition_id ON multipleconditions;
DROP INDEX ix_start_date__end_date ON multipleconditions;