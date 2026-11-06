RENAME TABLE searchable_outputs TO searchable_data;
ALTER TABLE searchable_data RENAME COLUMN `output_id` TO `data_id`, RENAME COLUMN `output_index` TO `data_index`;
ALTER TABLE searchable_data RENAME INDEX searchable_outputs_output_id__value TO searchable_data_data_id__value, RENAME INDEX searchable_outputs_execution_id TO searchable_data_execution_id;
