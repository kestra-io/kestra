CREATE TABLE IF NOT EXISTS task_outputs (
    "key" VARCHAR(250) PRIMARY KEY,
    "task_run_id" VARCHAR(150) NOT NULL,
    "tenant_id" VARCHAR(150) NOT NULL,
    "execution_id" VARCHAR(150) NOT NULL,
    "value" BYTEA,
    "uri" VARCHAR(250)
);