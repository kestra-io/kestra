-- =============================================================================
-- MIGRATION V001: Create Worker Groups Tables
-- =============================================================================
-- Description: Creates tables for managing worker groups and namespace mappings
-- Author: Kestra Platform Team
-- Date: 2025-11-12
-- =============================================================================

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- -----------------------------------------------------------------------------
-- Table: worker_groups
-- -----------------------------------------------------------------------------
-- Stores configuration for each worker group
CREATE TABLE worker_groups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,

    -- Resource configuration
    resource_cpu VARCHAR(50),
    resource_memory VARCHAR(50),
    resource_gpu VARCHAR(50),

    -- Limits
    max_concurrent_tasks INT DEFAULT 100 NOT NULL,
    max_queued_tasks INT DEFAULT 1000 NOT NULL,

    -- Features
    gpu_enabled BOOLEAN DEFAULT FALSE NOT NULL,
    auto_scaling_enabled BOOLEAN DEFAULT FALSE NOT NULL,
    min_replicas INT DEFAULT 1 NOT NULL,
    max_replicas INT DEFAULT 10 NOT NULL,

    -- Status
    status VARCHAR(50) DEFAULT 'active' NOT NULL,

    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(255),

    -- Constraints
    CONSTRAINT worker_groups_status_check CHECK (status IN ('active', 'inactive', 'maintenance')),
    CONSTRAINT worker_groups_replicas_check CHECK (min_replicas <= max_replicas),
    CONSTRAINT worker_groups_max_tasks_check CHECK (max_concurrent_tasks > 0)
);

-- Index for quick lookups by name
CREATE INDEX idx_worker_groups_name ON worker_groups(name);

-- Index for active worker groups
CREATE INDEX idx_worker_groups_status ON worker_groups(status);

-- -----------------------------------------------------------------------------
-- Table: namespace_worker_groups
-- -----------------------------------------------------------------------------
-- Maps namespace patterns to worker groups
CREATE TABLE namespace_worker_groups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Namespace pattern (regex)
    namespace_pattern VARCHAR(255) NOT NULL,

    -- Worker group reference
    worker_group_id UUID NOT NULL REFERENCES worker_groups(id) ON DELETE CASCADE,

    -- Priority (higher number = higher priority)
    -- When multiple patterns match, highest priority wins
    priority INT DEFAULT 0 NOT NULL,

    -- Optional description
    description TEXT,

    -- Status
    enabled BOOLEAN DEFAULT TRUE NOT NULL,

    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- Constraints
    CONSTRAINT namespace_worker_groups_unique UNIQUE(namespace_pattern, worker_group_id)
);

-- Index for fast pattern matching (ordered by priority)
CREATE INDEX idx_namespace_worker_groups_priority ON namespace_worker_groups(priority DESC);

-- Index for fast worker group lookups
CREATE INDEX idx_namespace_worker_groups_worker_group_id ON namespace_worker_groups(worker_group_id);

-- Index for enabled mappings only
CREATE INDEX idx_namespace_worker_groups_enabled ON namespace_worker_groups(enabled) WHERE enabled = TRUE;

-- -----------------------------------------------------------------------------
-- Table: worker_group_metrics
-- -----------------------------------------------------------------------------
-- Stores metrics for worker groups (for monitoring and auto-scaling)
CREATE TABLE worker_group_metrics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    worker_group_id UUID NOT NULL REFERENCES worker_groups(id) ON DELETE CASCADE,

    -- Timestamp
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Worker metrics
    active_workers INT NOT NULL DEFAULT 0,
    idle_workers INT NOT NULL DEFAULT 0,

    -- Task metrics
    queued_tasks INT NOT NULL DEFAULT 0,
    running_tasks INT NOT NULL DEFAULT 0,
    completed_tasks_last_hour INT NOT NULL DEFAULT 0,
    failed_tasks_last_hour INT NOT NULL DEFAULT 0,

    -- Performance metrics
    avg_task_duration_seconds FLOAT,
    p95_task_duration_seconds FLOAT,
    p99_task_duration_seconds FLOAT,

    -- Resource utilization
    cpu_utilization_percent FLOAT,
    memory_utilization_percent FLOAT,
    gpu_utilization_percent FLOAT,

    -- Constraints
    CONSTRAINT worker_group_metrics_valid_utilization CHECK (
        cpu_utilization_percent >= 0 AND cpu_utilization_percent <= 100 AND
        memory_utilization_percent >= 0 AND memory_utilization_percent <= 100 AND
        (gpu_utilization_percent IS NULL OR (gpu_utilization_percent >= 0 AND gpu_utilization_percent <= 100))
    )
);

-- Index for time-series queries
CREATE INDEX idx_worker_group_metrics_timestamp ON worker_group_metrics(worker_group_id, timestamp DESC);

-- Index for recent metrics (last 24 hours)
CREATE INDEX idx_worker_group_metrics_recent ON worker_group_metrics(timestamp DESC)
    WHERE timestamp > NOW() - INTERVAL '24 hours';

-- -----------------------------------------------------------------------------
-- Table: worker_group_events
-- -----------------------------------------------------------------------------
-- Audit log for worker group changes
CREATE TABLE worker_group_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    worker_group_id UUID REFERENCES worker_groups(id) ON DELETE SET NULL,

    -- Event details
    event_type VARCHAR(100) NOT NULL,
    event_data JSONB,

    -- User who triggered the event
    triggered_by VARCHAR(255),

    -- Timestamp
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- Constraints
    CONSTRAINT worker_group_events_type_check CHECK (event_type IN (
        'created', 'updated', 'deleted', 'scaled_up', 'scaled_down',
        'activated', 'deactivated', 'maintenance_started', 'maintenance_ended'
    ))
);

-- Index for audit queries
CREATE INDEX idx_worker_group_events_worker_group_id ON worker_group_events(worker_group_id, created_at DESC);

-- Index for recent events
CREATE INDEX idx_worker_group_events_created_at ON worker_group_events(created_at DESC);

-- -----------------------------------------------------------------------------
-- Trigger: Update worker_groups.updated_at on changes
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_worker_groups_updated_at
    BEFORE UPDATE ON worker_groups
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_namespace_worker_groups_updated_at
    BEFORE UPDATE ON namespace_worker_groups
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- -----------------------------------------------------------------------------
-- Comments
-- -----------------------------------------------------------------------------
COMMENT ON TABLE worker_groups IS 'Configuration and metadata for worker groups';
COMMENT ON TABLE namespace_worker_groups IS 'Maps namespace patterns to worker groups using regex';
COMMENT ON TABLE worker_group_metrics IS 'Time-series metrics for worker group monitoring and auto-scaling';
COMMENT ON TABLE worker_group_events IS 'Audit log for worker group lifecycle events';

COMMENT ON COLUMN namespace_worker_groups.priority IS 'Higher priority patterns are matched first when multiple patterns match a namespace';
COMMENT ON COLUMN worker_groups.status IS 'active: accepting tasks, inactive: not accepting tasks, maintenance: temporarily disabled';

-- =============================================================================
-- END OF MIGRATION V001
-- =============================================================================
