ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS "fulltext" TEXT GENERATED ALWAYS AS (
    JQ_STRING("value", '.id') || ' ' ||
    JQ_STRING("value", '.type') || ' ' ||
    COALESCE(JQ_STRING("value", '.server.hostname'), '') || ' ' ||
    COALESCE(JQ_STRING("value", '.server.version'), '')
);
