-- Postgres side of the 2.1.07-notification-items-owner migration; see
-- 2.1.07-notification-items-owner-h2.sql for why the column exists.
ALTER TABLE notification_items ADD COLUMN IF NOT EXISTS owner VARCHAR(150) GENERATED ALWAYS AS (value ->> 'owner') STORED;
CREATE INDEX IF NOT EXISTS notification_items_owner_outcome ON notification_items (owner, outcome);
