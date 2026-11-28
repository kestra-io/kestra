-- New table: AI usage per model call, so spend survives a restart and every webserver node agrees on it.
-- The token counts are generated columns rather than JSON reads because every query over this table is a SUM.
CREATE TABLE IF NOT EXISTS ai_usage (
    key                  VARCHAR(250) NOT NULL PRIMARY KEY,
    value                JSONB        NOT NULL,
    tenant_id            VARCHAR(150)          GENERATED ALWAYS AS (value ->> 'tenant') STORED,
    provider_id          VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'providerId') STORED,
    user_id              VARCHAR(150)          GENERATED ALWAYS AS (value ->> 'userId') STORED,
    recorded_at          TIMESTAMPTZ  NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'recordedAt')) STORED,
    prompt_tokens        BIGINT       NOT NULL GENERATED ALWAYS AS ((value ->> 'promptTokens')::BIGINT) STORED,
    cached_prompt_tokens BIGINT       NOT NULL GENERATED ALWAYS AS ((value ->> 'cachedPromptTokens')::BIGINT) STORED,
    completion_tokens    BIGINT       NOT NULL GENERATED ALWAYS AS ((value ->> 'completionTokens')::BIGINT) STORED,
    thought_tokens       BIGINT       NOT NULL GENERATED ALWAYS AS ((value ->> 'thoughtTokens')::BIGINT) STORED
);

-- Every read is "totals for this provider since a point in time", optionally narrowed to one user.
CREATE INDEX IF NOT EXISTS ai_usage__provider_recorded ON ai_usage (provider_id, recorded_at);
CREATE INDEX IF NOT EXISTS ai_usage__provider_user_recorded ON ai_usage (provider_id, user_id, recorded_at);
