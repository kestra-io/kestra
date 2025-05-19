alter table executions add "kind" VARCHAR(32) GENERATED ALWAYS AS (value ->> 'kind') STORED;
alter table logs add "execution_kind" VARCHAR(32) GENERATED ALWAYS AS (value ->> 'executionKind') STORED;
alter table metrics add "execution_kind" VARCHAR(32) GENERATED ALWAYS AS (value ->> 'executionKind') STORED;