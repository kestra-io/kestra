-- Fethr schema baseline: secrets, credentials and the tables registry.
--
-- Consolidated from the fork's V1_45__vault_secrets, V1_46__vault_credentials and V1_47__tables,
-- which no longer exist under 2.0's migration framework. Runs after every upstream 2.0.x script.
--
-- Every statement is idempotent: on a Fethr database upgraded in place the tables already exist
-- and each statement is a no-op; on a fresh install this creates them.
--
-- FULLTEXT_INDEX and UPDATE_UPDATED_DATETIME both predate the fork point and are also defined by
-- 2.0's own baseline, so they are available either way.

/* --- Dropped from the original scripts -----------------------------------------------------
 * Each of the three Flyway scripts ended with an ALTER TYPE queue_type ADD VALUE for its model
 * (io.kestra.core.models.vault.Secret, .vault.Credential, .table.TableDefinition). Queue 2.0
 * removed the queue_type enum outright -- the queues table now carries an INT type, and
 * 2.0.02-queue-drop-legacy runs DROP TYPE IF EXISTS queue_type -- so those statements would fail
 * against a type that no longer exists.
 *
 * QueueFactoryInterface still declares secret(), credential() and table() queues in the fork, so
 * the Credentials and Tables workstreams need to decide whether those named queues survive the
 * port and, if so, register them the way Queue 2.0 expects. That is a code question; nothing in
 * the schema depends on it.
 * ------------------------------------------------------------------------------------------ */


/* --- secrets (was V1_45__vault_secrets) ---------------------------------------------------- */
-- The tags column is new in 2.0: 2.0's secrets table renders and filters on tags, and this script
-- has not shipped anywhere, so the column belongs in the create rather than in a follow-up ALTER.
--
-- No data migration accompanies the namespace change. Resolution is now an exact namespace match
-- (see FethrSecretService), and the rows this table already holds were unreachable by any other
-- means anyway: the 1.x secrets API pinned every row to the "system" namespace, and an inheritance
-- chain from NamespaceUtils.asTree never yields "system" for a flow outside it. Only an explicit
-- secret(namespace='system', ...) ever resolved one, and exact matching serves that identically.
CREATE TABLE IF NOT EXISTS secrets (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    description TEXT GENERATED ALWAYS AS (value ->> 'description') STORED,
    deleted BOOL NOT NULL GENERATED ALWAYS AS ((value ->> 'deleted')::boolean) STORED,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Separate from the create so an existing Fethr database, whose secrets table predates tags, gets
-- the column too.
ALTER TABLE secrets ADD COLUMN IF NOT EXISTS tags JSONB GENERATED ALWAYS AS (value -> 'tags') STORED;

CREATE INDEX IF NOT EXISTS vault_secrets__tenant ON secrets ("deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS vault_secrets__tenant_key ON secrets ("key", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS vault_secrets__namespace ON secrets (deleted, namespace);
CREATE INDEX IF NOT EXISTS vault_secrets__namespace_key ON secrets (deleted, namespace, key);

CREATE OR REPLACE TRIGGER secrets_updated BEFORE UPDATE
    ON secrets FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();


/* --- credentials (was V1_46__vault_credentials) -------------------------------------------- */
CREATE TABLE IF NOT EXISTS credentials (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    namespace VARCHAR(150) GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    name VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'name') STORED,
    description TEXT GENERATED ALWAYS AS (value ->> 'description') STORED,
    type VARCHAR(50) NOT NULL GENERATED ALWAYS AS (value ->> 'type') STORED,
    deleted BOOL NOT NULL GENERATED ALWAYS AS ((value ->> 'deleted')::boolean) STORED,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value ->> 'name' AS VARCHAR)) ||
        FULLTEXT_INDEX(COALESCE(CAST(value ->> 'description' AS VARCHAR), ''))
    ) STORED
);

CREATE INDEX IF NOT EXISTS vault_credentials__tenant ON credentials ("deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS vault_credentials__tenant_name ON credentials ("name", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS vault_credentials__namespace ON credentials (deleted, namespace);
CREATE INDEX IF NOT EXISTS vault_credentials__namespace_name ON credentials (deleted, namespace, name);
CREATE INDEX IF NOT EXISTS vault_credentials__type ON credentials (deleted, tenant_id, type);
CREATE INDEX IF NOT EXISTS vault_credentials__fulltext ON credentials USING GIN (fulltext);

CREATE OR REPLACE TRIGGER credentials_updated BEFORE UPDATE
    ON credentials FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();


/* --- tables registry (was V1_47__tables) --------------------------------------------------- */
-- Only the registry. The user-defined tables it describes are created at runtime by
-- jdbc/tables/TableDdlSupport, not by any migration.
CREATE TABLE IF NOT EXISTS tables (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    namespace VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    name VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'name') STORED,
    physical_table_name VARCHAR(150) GENERATED ALWAYS AS (value ->> 'physicalTableName') STORED,
    description TEXT GENERATED ALWAYS AS (value ->> 'description') STORED,
    deleted BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS tables__tenant ON tables ("deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS tables__namespace ON tables (deleted, namespace);
CREATE INDEX IF NOT EXISTS tables__namespace_name ON tables (deleted, namespace, name);

CREATE OR REPLACE TRIGGER tables_updated BEFORE UPDATE
    ON tables FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();
