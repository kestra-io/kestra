alter table flows add "updated" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.updated'));
