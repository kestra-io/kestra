-- Add updated timestamp column to flows table for tracking revision creation time
ALTER TABLE flows ADD COLUMN IF NOT EXISTS "updated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Create index for efficient ordering by timestamp
CREATE INDEX IF NOT EXISTS flows_updated ON flows ("updated");
