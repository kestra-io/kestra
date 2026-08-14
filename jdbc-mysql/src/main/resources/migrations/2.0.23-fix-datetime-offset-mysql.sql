-- Fixes the UTC-offset extraction in the multipleconditions date columns.
--
-- The offset written by the `.SSSXXX` serializer is always 6 characters (`+02:00`, `-03:30`), but
-- the extraction read only 5 of them: SUBSTRING(v, LENGTH(v) - 5, 5) yields `-03:3` for `-03:30`,
-- which CONVERT_TZ then reads as `-03:03`. Offsets whose minutes part is `00` survived by accident
-- because the dropped character was a `0`, so only timezones with a non-zero minutes part were
-- affected -- Newfoundland (-03:30), Marquesas (-09:30), India (+05:30), Nepal (+05:45),
-- Adelaide (+09:30), Iran, Myanmar, Chatham. The sibling SUBSTRING(v, 1, LENGTH(v) - 6) that
-- strips the offset off the datetime part already used 6, which is the disagreement that gave the
-- bug away. RIGHT(v, 6) takes the whole offset.
ALTER TABLE multipleconditions MODIFY COLUMN `start_date` DATETIME(6) GENERATED ALWAYS AS (
    IF(
        SUBSTRING(value ->> '$.start', LENGTH(value ->> '$.start'), LENGTH(value ->> '$.start')) = 'Z',
        STR_TO_DATE(value ->> '$.start', '%Y-%m-%dT%H:%i:%s.%fZ'),
        CONVERT_TZ(
            STR_TO_DATE(SUBSTRING(value ->> '$.start', 1, LENGTH(value ->> '$.start') - 6), '%Y-%m-%dT%H:%i:%s.%f'),
            RIGHT(value ->> '$.start', 6),
            'UTC'
        )
    )
) STORED NOT NULL;

ALTER TABLE multipleconditions MODIFY COLUMN `end_date` DATETIME(6) GENERATED ALWAYS AS (
    IF(
        SUBSTRING(value ->> '$.end', LENGTH(value ->> '$.end'), LENGTH(value ->> '$.end')) = 'Z',
        STR_TO_DATE(value ->> '$.end', '%Y-%m-%dT%H:%i:%s.%fZ'),
        CONVERT_TZ(
            STR_TO_DATE(SUBSTRING(value ->> '$.end', 1, LENGTH(value ->> '$.end') - 6), '%Y-%m-%dT%H:%i:%s.%f'),
            RIGHT(value ->> '$.end', 6),
            'UTC'
        )
    )
) STORED NOT NULL;
