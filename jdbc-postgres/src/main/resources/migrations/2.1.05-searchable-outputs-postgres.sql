CREATE TABLE IF NOT EXISTS searchable_outputs (
    "tenant_id"    VARCHAR(150) NOT NULL,
    "execution_id" VARCHAR(150) NOT NULL,
    "output_id"    VARCHAR(150) NOT NULL,
    "output_index" INT          NOT NULL,
    "value"        VARCHAR(2048),
    PRIMARY KEY ("tenant_id", "execution_id", "output_id", "output_index")
);

CREATE INDEX IF NOT EXISTS searchable_outputs_output_id__value ON searchable_outputs ("tenant_id", "output_id", "value");

CREATE INDEX IF NOT EXISTS searchable_outputs_execution_id ON searchable_outputs ("execution_id");
