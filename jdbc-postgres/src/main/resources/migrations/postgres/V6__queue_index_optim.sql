-- We drop the PK and the queues_type__offset, otherwise they are used by the poll query which is sub-optimal.
-- We create an hash index on offset that will be used instead when filtering on offset.

ALTER TABLE public.queues DROP CONSTRAINT queues_pkey;

DROP INDEX queues_type__offset;

CREATE INDEX queues_offset ON public.queues USING hash ("offset");
