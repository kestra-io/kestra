-- make state_duration nullable
ALTER TABLE executions ALTER COLUMN "state_duration" DROP NOT NULL;
