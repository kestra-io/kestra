/* Change worker_job_running.worker_uuid to extract 'uid' instead of 'workerUuid' from workerInstance */
DROP INDEX IF EXISTS worker_job_running_worker_uuid;

ALTER TABLE worker_job_running DROP COLUMN worker_uuid;

ALTER TABLE worker_job_running
    ADD COLUMN worker_uid VARCHAR(36) NOT NULL GENERATED ALWAYS AS (value -> 'workerInstance' ->> 'uid') STORED;

CREATE INDEX IF NOT EXISTS worker_job_running_worker_uid ON worker_job_running (worker_uid);