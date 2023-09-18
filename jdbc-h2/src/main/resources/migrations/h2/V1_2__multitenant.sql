alter table flows add "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId'));
alter table executions add "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId'));
alter table templates add "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId'));
alter table logs add "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId'));
alter table metrics add "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId'));
alter table flow_topologies add "source_tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.source.tenantId'));
alter table flow_topologies add "destination_tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.destination.tenantId'));
alter table triggers add "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId'));