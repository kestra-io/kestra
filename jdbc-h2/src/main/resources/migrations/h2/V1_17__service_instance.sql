/* ----------------------- ServiceInstance ----------------------- */
CREATE TABLE IF NOT EXISTS service_instance
(
    "key"          VARCHAR(250)  NOT NULL PRIMARY KEY,
    "value"        VARCHAR(1000) NOT NULL,
    "service_id"   VARCHAR(36) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "service_type" VARCHAR(36) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.type')),
    "state"        VARCHAR(36) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.state')),
    "created_at"   TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.createdAt'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "updated_at"   TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(JQ_STRING("value", '.updatedAt'), 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);