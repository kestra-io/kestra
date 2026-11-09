-- 2.1.07: an async operation run in-process by one service (a plugin install on a webserver) records
-- that service as the owner of its items, so the items left PENDING can be failed once the service is
-- reported dead. Canonical rationale for the Postgres and MySQL sides of this migration.
ALTER TABLE notification_items ADD COLUMN IF NOT EXISTS "owner" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.owner'));
CREATE INDEX IF NOT EXISTS notification_items_owner_outcome ON notification_items ("owner", "outcome");
