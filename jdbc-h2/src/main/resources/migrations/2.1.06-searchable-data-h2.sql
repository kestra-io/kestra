ALTER TABLE searchable_outputs RENAME TO searchable_data;
ALTER TABLE searchable_data ALTER COLUMN "output_id" RENAME TO "data_id";
ALTER TABLE searchable_data ALTER COLUMN "output_index" RENAME TO "data_index";
ALTER INDEX IF EXISTS searchable_outputs_output_id__value RENAME TO searchable_data_data_id__value;
ALTER INDEX IF EXISTS searchable_outputs_execution_id RENAME TO searchable_data_execution_id;
