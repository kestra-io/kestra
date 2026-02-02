/* Change worker_job_running.worker_uuid to extract 'uid' instead of 'workerUuid' from workerInstance */
ALTER TABLE worker_job_running
    DROP COLUMN `worker_uuid`;

ALTER TABLE worker_job_running
    ADD COLUMN `worker_uid` VARCHAR(36) GENERATED ALWAYS AS (value ->> '$.workerInstance.uid') STORED NOT NULL,
    ADD INDEX ix_worker_uid (worker_uid);