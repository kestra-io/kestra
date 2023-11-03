alter table multipleconditions add "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED;

DROP INDEX IF EXISTS multipleconditions_namespace__flow_id__condition_id;
DROP INDEX IF EXISTS multipleconditions_start_date__end_date;
CREATE INDEX IF NOT EXISTS multipleconditions_namespace__flow_id__condition_id ON multipleconditions (tenant_id, namespace, flow_id, condition_id);
CREATE INDEX IF NOT EXISTS multipleconditions_start_date__end_date ON multipleconditions (tenant_id, start_date, end_date);