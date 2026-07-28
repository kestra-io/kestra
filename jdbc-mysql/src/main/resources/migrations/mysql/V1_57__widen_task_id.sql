-- Widen the generated task_id column from 150 to 256 to match Task.id's @Size(max = 256).
-- Plugin-generated taskIds (e.g. Ansible "<host> | <play> : <task>") can exceed 150 chars and
-- overflow the VARCHAR(150) column, crashing the JDBC indexer. MySQL requires restating the full
-- generation expression; modifying a STORED generated column rebuilds the table (ALGORITHM=COPY).
ALTER TABLE logs MODIFY COLUMN `task_id` VARCHAR(256) GENERATED ALWAYS AS (value ->> '$.taskId') STORED;
ALTER TABLE metrics MODIFY COLUMN `task_id` VARCHAR(256) GENERATED ALWAYS AS (value ->> '$.taskId') STORED;
