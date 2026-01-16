ALTER TABLE triggers
ADD COLUMN `disabled` BOOL
GENERATED ALWAYS AS (value ->> '$.disabled' = 'true') STORED NOT NULL