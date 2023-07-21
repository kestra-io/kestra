ALTER TABLE triggers ADD COLUMN IF NOT EXISTS "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
    JQ_STRING("value", '.flowId') ||
    JQ_STRING("value", '.namespace') ||
    JQ_STRING("value", '.triggerId') ||
    COALESCE(JQ_STRING("value", '.executionId'), '')
)