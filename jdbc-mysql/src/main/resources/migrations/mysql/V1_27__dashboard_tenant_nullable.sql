ALTER TABLE dashboards
    MODIFY COLUMN `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED;