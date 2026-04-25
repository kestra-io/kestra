-- Add a generated column derived from the JSON value so the SQL "latest non-draft revision"
-- query can filter on it directly. COALESCE keeps legacy rows whose JSON does not yet carry
-- the field as published (draft = false).
ALTER TABLE `flows` ADD COLUMN `draft` BOOL GENERATED ALWAYS AS (COALESCE(value ->> '$.draft' = 'true', FALSE)) STORED NOT NULL;

CREATE INDEX ix_flows_draft ON `flows` (`deleted`, `draft`, `namespace`, `id`, `revision`);
