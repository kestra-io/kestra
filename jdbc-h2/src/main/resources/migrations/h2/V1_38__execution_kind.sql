alter table executions add "kind" VARCHAR(32) GENERATED ALWAYS AS (JQ_STRING("value", '.kind'));
alter table logs add "execution_kind" VARCHAR(32) GENERATED ALWAYS AS (JQ_STRING("value", '.executionKind'));
alter table metrics add "execution_kind" VARCHAR(32) GENERATED ALWAYS AS (JQ_STRING("value", '.executionKind'));