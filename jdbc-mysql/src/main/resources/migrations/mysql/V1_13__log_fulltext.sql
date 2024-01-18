ALTER TABLE `logs` MODIFY `execution_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.executionId') STORED;
