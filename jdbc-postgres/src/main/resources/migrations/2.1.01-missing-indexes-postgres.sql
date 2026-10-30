-- Postgres side of the 2.1.01-missing-indexes fix (see 2.1.01-missing-indexes-mysql.sql for the
-- reverse gap on multipleconditions).
--
-- MySQL has indexed flow_topologies on both (destination_tenant_id, destination_namespace,
-- destination_id) and (source_tenant_id, source_namespace, source_id) since baseline. Postgres
-- only ever had the destination index, plus a 6-column destination+source composite whose leftmost
-- columns are still destination_*, so it cannot serve a source-only lookup. Both findByFlow() and
-- save() in AbstractJdbcFlowTopologyRepository filter on (tenant_id, source_namespace, source_id)
-- alone as one branch of an OR, and save() runs on every flow save, so that branch was a full table
-- scan. Replace the composite with a dedicated source index, matching MySQL's shape.
DROP INDEX CONCURRENTLY IF EXISTS flow_topologies_destination__source;

CREATE INDEX CONCURRENTLY IF NOT EXISTS flow_topologies_source ON flow_topologies (source_tenant_id, source_namespace, source_id);
