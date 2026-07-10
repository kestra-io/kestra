-- Widen the metrics.task_id generated column from 150 to 256 to match Task.id's @Size(max = 256).
-- Modifying a STORED GENERATED column rebuilds the table (ALGORITHM=COPY), so the ALTER is guarded by
-- an information_schema length check and runs only when the column is still shorter than 256 —
-- skipping the rebuild for installs already at 256 (e.g. arriving from Kestra 1.3.x).
SET @len = (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'metrics' AND column_name = 'task_id');
SET @sql = IF(@len IS NOT NULL AND @len < 256, 'ALTER TABLE metrics MODIFY COLUMN `task_id` VARCHAR(256) GENERATED ALWAYS AS (value ->> ''$.taskId'') STORED', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
