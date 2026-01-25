-- Add updated column for tracking revision creation time (extracted from value JSON)
alter table flows add "updated" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.updated'));
