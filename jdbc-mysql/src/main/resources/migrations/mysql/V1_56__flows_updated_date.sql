alter table flows add `updated` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.updated') STORED;
