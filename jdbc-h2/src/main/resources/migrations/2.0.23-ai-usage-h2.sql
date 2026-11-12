-- New table: AI usage per model call, so spend survives a restart and every webserver node agrees on it.
-- The token counts are generated columns rather than JSON reads because every query over this table is a SUM.
CREATE TABLE IF NOT EXISTS ai_usage (
    "key"                   VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"                 TEXT         NOT NULL,
    "tenant_id"             VARCHAR(150)          GENERATED ALWAYS AS (JQ_STRING("value", '.tenant')),
    "provider_id"           VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.providerId')),
    "user_id"               VARCHAR(150)          GENERATED ALWAYS AS (JQ_STRING("value", '.userId')),
    "recorded_at"           TIMESTAMP    NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.recordedAt'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "prompt_tokens"         BIGINT       NOT NULL GENERATED ALWAYS AS (JQ_LONG("value", '.promptTokens')),
    "cached_prompt_tokens"  BIGINT       NOT NULL GENERATED ALWAYS AS (JQ_LONG("value", '.cachedPromptTokens')),
    "completion_tokens"     BIGINT       NOT NULL GENERATED ALWAYS AS (JQ_LONG("value", '.completionTokens')),
    "thought_tokens"        BIGINT       NOT NULL GENERATED ALWAYS AS (JQ_LONG("value", '.thoughtTokens'))
);

CREATE INDEX IF NOT EXISTS ai_usage__provider_recorded ON ai_usage ("provider_id", "recorded_at");
CREATE INDEX IF NOT EXISTS ai_usage__provider_user_recorded ON ai_usage ("provider_id", "user_id", "recorded_at");
