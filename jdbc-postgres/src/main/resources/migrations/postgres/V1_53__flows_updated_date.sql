-- Add updated column for tracking revision creation time (extracted from value JSON)
alter table flows add "updated" VARCHAR(250) GENERATED ALWAYS AS (value ->> 'updated') STORED;
