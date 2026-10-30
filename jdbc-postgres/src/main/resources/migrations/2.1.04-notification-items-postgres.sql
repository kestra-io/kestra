CREATE TABLE IF NOT EXISTS notification_items (
    key           VARCHAR(700) NOT NULL PRIMARY KEY,
    value         JSONB        NOT NULL,
    tenant_id     VARCHAR(150) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    operation_id  VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'operationId') STORED,
    resource_id   VARCHAR(500) NOT NULL GENERATED ALWAYS AS (value ->> 'resourceId') STORED,
    outcome       VARCHAR(50)  NOT NULL GENERATED ALWAYS AS (value ->> 'outcome') STORED
);

CREATE INDEX IF NOT EXISTS notification_items_tenant_operation ON notification_items (tenant_id, operation_id);
CREATE INDEX IF NOT EXISTS notification_items_operation_resource ON notification_items (operation_id, resource_id);
