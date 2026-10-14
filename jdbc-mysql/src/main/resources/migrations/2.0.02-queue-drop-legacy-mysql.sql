-- Drops a genuine pre-Queue-2.0 (Flyway-managed) queues table. Only executed by
-- AbstractV2_0_02QueueMigration when it has confirmed, via JDBC metadata, that the table exists but
-- has no routing_key column -- i.e. it predates Queue 2.0 and cannot hold live 2.0 queue messages.
-- IF EXISTS keeps this safe to retry after a partial failure in the same migration run.
DROP TABLE IF EXISTS queues;
