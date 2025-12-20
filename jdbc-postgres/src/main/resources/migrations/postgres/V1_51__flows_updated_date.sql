-- Add updated timestamp column to flows table for tracking revision creation time
ALTER TABLE flows ADD COLUMN IF NOT EXISTS updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Create index for efficient ordering by timestamp
CREATE INDEX IF NOT EXISTS flows_updated ON flows (updated);

-- Create trigger to auto-update timestamp on each insert/update
CREATE OR REPLACE TRIGGER flows_updated BEFORE INSERT OR UPDATE
    ON flows FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();
