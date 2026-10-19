CREATE TABLE IF NOT EXISTS notifications (
    "key"           VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"         TEXT         NOT NULL,
    "id"            VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "user_id"       VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.userId')),
    "tenant_id"     VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "type"          VARCHAR(50)  NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.type')),
    "read"          BOOL         NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.read')),
    "reference_id"  VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.referenceId')),
    "created_date"  TIMESTAMP    NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.createdDate'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "updated_date"  TIMESTAMP    NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.updatedDate'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))
);

CREATE INDEX IF NOT EXISTS notifications_user_updated ON notifications ("user_id", "updated_date");
CREATE INDEX IF NOT EXISTS notifications_user_created ON notifications ("user_id", "created_date");
CREATE INDEX IF NOT EXISTS notifications_user_read ON notifications ("user_id", "read");
CREATE INDEX IF NOT EXISTS notifications_user_type_reference ON notifications ("user_id", "type", "reference_id");
