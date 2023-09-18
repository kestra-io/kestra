alter table flows add `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED;
alter table executions add `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED;
alter table templates add `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED;
alter table logs add `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED;
alter table metrics add `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED;
alter table flow_topologies add `source_tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.source.tenantId') STORED;
alter table flow_topologies add `destination_tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.destination.tenantId') STORED;
alter table triggers add `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED;