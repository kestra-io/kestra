-- 2.0.18: expose the originating Loop task id (loopRun.taskId) as a queryable generated column on
-- executions, so LOOP-kind iteration executions can be filtered per loop task. Mirrors loop_run_index.
ALTER TABLE executions ADD COLUMN IF NOT EXISTS "loop_run_task_id" VARCHAR(256) GENERATED ALWAYS AS (value #>> '{loopRun,taskId}') STORED;
