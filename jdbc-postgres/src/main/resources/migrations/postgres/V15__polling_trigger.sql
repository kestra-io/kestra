ALTER TYPE queue_type ADD VALUE 'io.kestra.core.runners.WorkerTrigger';
ALTER TYPE queue_type ADD VALUE 'io.kestra.core.runners.WorkerTriggerResult';

-- trigger logs have no execution id
alter table logs alter column execution_id drop not null;