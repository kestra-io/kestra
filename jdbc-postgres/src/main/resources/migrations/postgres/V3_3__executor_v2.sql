ALTER TYPE queue_type ADD VALUE IF NOT EXISTS 'io.kestra.core.runners.ExecutionEvent';

DROP TABLE IF EXISTS executorstate;