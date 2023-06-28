ALTER TYPE queue_type ADD VALUE IF NOT EXISTS 'io.kestra.core.runners.WorkerTriggerResult';
ALTER TYPE queue_type RENAME VALUE 'io.kestra.core.runners.WorkerTask' TO 'io.kestra.core.runners.WorkerJob';

-- trigger logs have no execution id
alter table logs alter column execution_id drop not null;