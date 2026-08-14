-- New table: AI usage per model call, so spend survives a restart and every webserver node agrees on it.
-- The token counts are generated columns rather than JSON reads because every query over this table is a SUM.
CREATE TABLE IF NOT EXISTS ai_usage (
    `key`                  VARCHAR(250) NOT NULL PRIMARY KEY,
    `value`                JSON         NOT NULL,
    `tenant_id`            VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.tenant') STORED NULL,
    `provider_id`          VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.providerId') STORED NOT NULL,
    `user_id`              VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.userId') STORED NULL,
    `recorded_at`          DATETIME(6)  GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.recordedAt', '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `prompt_tokens`        BIGINT       GENERATED ALWAYS AS (value ->> '$.promptTokens') STORED NOT NULL,
    `cached_prompt_tokens` BIGINT       GENERATED ALWAYS AS (value ->> '$.cachedPromptTokens') STORED NOT NULL,
    `completion_tokens`    BIGINT       GENERATED ALWAYS AS (value ->> '$.completionTokens') STORED NOT NULL,
    `thought_tokens`       BIGINT       GENERATED ALWAYS AS (value ->> '$.thoughtTokens') STORED NOT NULL,
    INDEX ai_usage__provider_recorded (`tenant_id`, `provider_id`, `recorded_at`),
    INDEX ai_usage__provider_user_recorded (`tenant_id`, `provider_id`, `user_id`, `recorded_at`)
);
