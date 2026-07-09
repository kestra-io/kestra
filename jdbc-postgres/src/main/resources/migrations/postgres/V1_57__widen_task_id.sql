-- Widen the generated task_id column from 150 to 256 to match Task.id's @Size(max = 256).
-- Plugin-generated taskIds (e.g. Ansible "<host> | <play> : <task>") can exceed 150 chars and
-- overflow the VARCHAR(150) column, crashing the JDBC indexer. In Postgres this is a metadata-only
-- change (no table rewrite). NOT NULL on metrics.task_id is preserved by ALTER ... TYPE.
ALTER TABLE logs ALTER COLUMN task_id TYPE VARCHAR(256);
ALTER TABLE metrics ALTER COLUMN task_id TYPE VARCHAR(256);
