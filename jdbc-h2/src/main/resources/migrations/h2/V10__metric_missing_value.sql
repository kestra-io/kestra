ALTER TABLE metrics ADD COLUMN "metric_value" DOUBLE GENERATED ALWAYS AS (JQ_DOUBLE("value", '.value'));