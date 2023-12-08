alter table logs alter column "execution_id" drop not null;

ALTER TABLE logs
    ALTER COLUMN  "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
        JQ_STRING("value", '.namespace') ||
        JQ_STRING("value", '.flowId') ||
        COALESCE(JQ_STRING("value", '.taskId'), '') ||
        COALESCE(JQ_STRING("value", '.executionId'), '') ||
        COALESCE(JQ_STRING("value", '.taskRunId'), '') ||
        COALESCE(JQ_STRING("value", '.triggerId'), '') ||
        COALESCE(JQ_STRING("value", '.message'), '') ||
        COALESCE(JQ_STRING("value", '.thread'), '')
    );