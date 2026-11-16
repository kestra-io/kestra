ALTER TABLE searchable_outputs RENAME TO searchable_data;
ALTER TABLE searchable_data RENAME COLUMN "output_id" TO "data_id";
ALTER TABLE searchable_data RENAME COLUMN "output_index" TO "data_index";
ALTER TABLE searchable_data RENAME CONSTRAINT searchable_outputs_pkey TO searchable_data_pkey;
ALTER INDEX IF EXISTS searchable_outputs_output_id__value RENAME TO searchable_data_data_id__value;
ALTER INDEX IF EXISTS searchable_outputs_execution_id RENAME TO searchable_data_execution_id;
