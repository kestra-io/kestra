-- =============================================================================
-- GOVERNANCE DATABASE SCHEMA
-- =============================================================================
-- Version: 2.0.0
-- Purpose: AI Agent governance, compliance, and audit trails
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Table: agent_audit_trail
-- Purpose: Complete audit trail of all agent executions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_audit_trail (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(255) NOT NULL UNIQUE,
    customer_id VARCHAR(255) NOT NULL,
    agent_type VARCHAR(100) NOT NULL,
    agent_version VARCHAR(50) DEFAULT '1.0.0',
    query TEXT NOT NULL,
    response TEXT NOT NULL,
    steps_taken INTEGER DEFAULT 0,
    execution_time_ms INTEGER,
    quality_score DECIMAL(3,2),
    compliance_score DECIMAL(3,2),
    langfuse_trace_url TEXT,
    langfuse_session_id VARCHAR(255),
    pii_detected BOOLEAN DEFAULT FALSE,
    pii_entities JSONB DEFAULT '[]'::jsonb,
    cost_usd DECIMAL(10,6),
    tokens_prompt INTEGER,
    tokens_completion INTEGER,
    tokens_total INTEGER,
    model VARCHAR(100),
    status VARCHAR(50) DEFAULT 'completed', -- 'completed', 'failed', 'timeout'
    error_message TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_audit_customer_id ON agent_audit_trail(customer_id);
CREATE INDEX idx_audit_created_at ON agent_audit_trail(created_at);
CREATE INDEX idx_audit_agent_type ON agent_audit_trail(agent_type);
CREATE INDEX idx_audit_pii_detected ON agent_audit_trail(pii_detected);
CREATE INDEX idx_audit_status ON agent_audit_trail(status);
CREATE INDEX idx_audit_customer_date ON agent_audit_trail(customer_id, created_at);

-- Comment
COMMENT ON TABLE agent_audit_trail IS 'Complete audit trail of all AI agent executions for compliance and governance';

-- -----------------------------------------------------------------------------
-- Table: quality_metrics
-- Purpose: Detailed quality scores for agent responses
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quality_metrics (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(255) NOT NULL,
    accuracy_score DECIMAL(3,2),
    helpfulness_score DECIMAL(3,2),
    professionalism_score DECIMAL(3,2),
    completeness_score DECIMAL(3,2),
    clarity_score DECIMAL(3,2),
    overall_score DECIMAL(3,2) NOT NULL,
    evaluated_by VARCHAR(50) NOT NULL, -- 'auto', 'human', 'llm'
    evaluator_model VARCHAR(100), -- e.g., 'gpt-4' if llm
    feedback TEXT,
    improvement_suggestions JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (execution_id) REFERENCES agent_audit_trail(execution_id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_quality_execution_id ON quality_metrics(execution_id);
CREATE INDEX idx_quality_overall_score ON quality_metrics(overall_score);
CREATE INDEX idx_quality_created_at ON quality_metrics(created_at);

COMMENT ON TABLE quality_metrics IS 'Quality evaluation scores for agent responses';

-- -----------------------------------------------------------------------------
-- Table: compliance_violations
-- Purpose: Track compliance violations and policy breaches
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compliance_violations (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(255) NOT NULL,
    violation_type VARCHAR(100) NOT NULL,
    -- Types: 'PII_DETECTED', 'PROFANITY', 'HARMFUL_CONTENT', 'POLICY_VIOLATION',
    --        'DATA_LEAK', 'UNAUTHORIZED_ACCESS', 'COST_LIMIT_EXCEEDED'
    severity VARCHAR(50) NOT NULL, -- 'low', 'medium', 'high', 'critical'
    details JSONB NOT NULL,
    affected_data TEXT,
    risk_score DECIMAL(3,2), -- 0.00 to 10.00
    resolved BOOLEAN DEFAULT FALSE,
    resolution_notes TEXT,
    resolved_by VARCHAR(255),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (execution_id) REFERENCES agent_audit_trail(execution_id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_violations_execution_id ON compliance_violations(execution_id);
CREATE INDEX idx_violations_type ON compliance_violations(violation_type);
CREATE INDEX idx_violations_severity ON compliance_violations(severity);
CREATE INDEX idx_violations_resolved ON compliance_violations(resolved);
CREATE INDEX idx_violations_created_at ON compliance_violations(created_at);

COMMENT ON TABLE compliance_violations IS 'Track and manage compliance violations and security incidents';

-- -----------------------------------------------------------------------------
-- Table: customer_usage
-- Purpose: Daily usage tracking per customer for billing and cost control
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer_usage (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    agent_executions INTEGER DEFAULT 0,
    successful_executions INTEGER DEFAULT 0,
    failed_executions INTEGER DEFAULT 0,
    total_cost_usd DECIMAL(10,2) DEFAULT 0.00,
    total_tokens INTEGER DEFAULT 0,
    avg_latency_ms INTEGER,
    avg_quality_score DECIMAL(3,2),
    pii_incidents INTEGER DEFAULT 0,
    compliance_violations INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(customer_id, date)
);

-- Indexes
CREATE INDEX idx_usage_customer_id ON customer_usage(customer_id);
CREATE INDEX idx_usage_date ON customer_usage(date);
CREATE INDEX idx_usage_customer_date ON customer_usage(customer_id, date);

COMMENT ON TABLE customer_usage IS 'Daily aggregated usage metrics per customer for billing and monitoring';

-- -----------------------------------------------------------------------------
-- Table: customer_budgets
-- Purpose: Monthly budget limits per customer for cost control
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer_budgets (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    month_year VARCHAR(7) NOT NULL, -- Format: 'YYYY-MM'
    budget_limit_usd DECIMAL(10,2) NOT NULL,
    current_usage_usd DECIMAL(10,2) DEFAULT 0.00,
    alert_threshold_percent INTEGER DEFAULT 80, -- Alert at 80% of budget
    alert_sent BOOLEAN DEFAULT FALSE,
    budget_exceeded BOOLEAN DEFAULT FALSE,
    exceeded_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(customer_id, month_year)
);

-- Indexes
CREATE INDEX idx_budgets_customer_id ON customer_budgets(customer_id);
CREATE INDEX idx_budgets_month_year ON customer_budgets(month_year);
CREATE INDEX idx_budgets_exceeded ON customer_budgets(budget_exceeded);

COMMENT ON TABLE customer_budgets IS 'Monthly budget limits and tracking per customer';

-- -----------------------------------------------------------------------------
-- Table: user_feedback
-- Purpose: Collect user feedback (thumbs up/down, comments)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_feedback (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    feedback_type VARCHAR(50) NOT NULL, -- 'thumbs_up', 'thumbs_down', 'comment', 'rating'
    rating INTEGER, -- 1-5 stars (optional)
    comment TEXT,
    sentiment VARCHAR(50), -- 'positive', 'negative', 'neutral' (auto-detected)
    created_at TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (execution_id) REFERENCES agent_audit_trail(execution_id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_feedback_execution_id ON user_feedback(execution_id);
CREATE INDEX idx_feedback_customer_id ON user_feedback(customer_id);
CREATE INDEX idx_feedback_type ON user_feedback(feedback_type);
CREATE INDEX idx_feedback_created_at ON user_feedback(created_at);

COMMENT ON TABLE user_feedback IS 'User feedback on agent responses for continuous improvement';

-- -----------------------------------------------------------------------------
-- Table: prompt_versions
-- Purpose: Version control for AI prompts
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prompt_versions (
    id BIGSERIAL PRIMARY KEY,
    prompt_name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    prompt_text TEXT NOT NULL,
    model VARCHAR(100),
    temperature DECIMAL(3,2),
    max_tokens INTEGER,
    status VARCHAR(50) DEFAULT 'draft', -- 'draft', 'testing', 'production', 'archived'
    performance_score DECIMAL(3,2), -- Average quality score with this prompt
    usage_count INTEGER DEFAULT 0,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    activated_at TIMESTAMP,
    archived_at TIMESTAMP,
    metadata JSONB DEFAULT '{}'::jsonb,
    UNIQUE(prompt_name, version)
);

-- Indexes
CREATE INDEX idx_prompts_name ON prompt_versions(prompt_name);
CREATE INDEX idx_prompts_status ON prompt_versions(status);
CREATE INDEX idx_prompts_created_at ON prompt_versions(created_at);

COMMENT ON TABLE prompt_versions IS 'Version control and A/B testing for AI prompts';

-- -----------------------------------------------------------------------------
-- Table: gdpr_deletion_log
-- Purpose: Track GDPR right-to-be-forgotten requests
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gdpr_deletion_log (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    request_type VARCHAR(50) NOT NULL, -- 'deletion', 'anonymization', 'export'
    requested_by VARCHAR(255),
    requested_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP,
    status VARCHAR(50) DEFAULT 'pending', -- 'pending', 'in_progress', 'completed', 'failed'
    data_deleted JSONB, -- List of tables/records affected
    verification_code VARCHAR(100),
    notes TEXT
);

-- Indexes
CREATE INDEX idx_gdpr_customer_id ON gdpr_deletion_log(customer_id);
CREATE INDEX idx_gdpr_status ON gdpr_deletion_log(status);
CREATE INDEX idx_gdpr_requested_at ON gdpr_deletion_log(requested_at);

COMMENT ON TABLE gdpr_deletion_log IS 'Audit trail for GDPR data deletion and anonymization requests';

-- -----------------------------------------------------------------------------
-- Table: security_events
-- Purpose: Log security-related events
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS security_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    -- Types: 'unauthorized_access', 'rate_limit_exceeded', 'suspicious_activity',
    --        'data_exfiltration_attempt', 'malicious_input'
    severity VARCHAR(50) NOT NULL, -- 'info', 'warning', 'critical'
    execution_id VARCHAR(255),
    customer_id VARCHAR(255),
    ip_address VARCHAR(50),
    user_agent TEXT,
    details JSONB NOT NULL,
    action_taken VARCHAR(255), -- e.g., 'blocked', 'alerted', 'logged'
    created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_security_event_type ON security_events(event_type);
CREATE INDEX idx_security_severity ON security_events(severity);
CREATE INDEX idx_security_customer_id ON security_events(customer_id);
CREATE INDEX idx_security_created_at ON security_events(created_at);

COMMENT ON TABLE security_events IS 'Security event logging and incident tracking';

-- -----------------------------------------------------------------------------
-- TRIGGERS: Auto-update timestamps
-- -----------------------------------------------------------------------------

-- Trigger for agent_audit_trail
CREATE OR REPLACE FUNCTION update_audit_trail_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_audit_trail_timestamp
    BEFORE UPDATE ON agent_audit_trail
    FOR EACH ROW
    EXECUTE FUNCTION update_audit_trail_timestamp();

-- Trigger for customer_usage
CREATE OR REPLACE FUNCTION update_customer_usage_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_customer_usage_timestamp
    BEFORE UPDATE ON customer_usage
    FOR EACH ROW
    EXECUTE FUNCTION update_customer_usage_timestamp();

-- Trigger for customer_budgets
CREATE OR REPLACE FUNCTION update_customer_budgets_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_customer_budgets_timestamp
    BEFORE UPDATE ON customer_budgets
    FOR EACH ROW
    EXECUTE FUNCTION update_customer_budgets_timestamp();

-- -----------------------------------------------------------------------------
-- VIEWS: Helpful analytics views
-- -----------------------------------------------------------------------------

-- View: Daily governance summary
CREATE OR REPLACE VIEW daily_governance_summary AS
SELECT
    DATE(created_at) as date,
    COUNT(*) as total_executions,
    COUNT(*) FILTER (WHERE status = 'completed') as successful_executions,
    COUNT(*) FILTER (WHERE status = 'failed') as failed_executions,
    AVG(execution_time_ms) as avg_latency_ms,
    AVG(quality_score) as avg_quality_score,
    SUM(cost_usd) as total_cost_usd,
    SUM(tokens_total) as total_tokens,
    COUNT(*) FILTER (WHERE pii_detected = true) as pii_incidents
FROM agent_audit_trail
GROUP BY DATE(created_at)
ORDER BY date DESC;

-- View: Customer health score
CREATE OR REPLACE VIEW customer_health_score AS
SELECT
    customer_id,
    COUNT(*) as total_executions,
    AVG(quality_score) as avg_quality,
    SUM(cost_usd) as total_cost,
    COUNT(*) FILTER (WHERE pii_detected = true) as pii_count,
    (
        SELECT COUNT(*)
        FROM compliance_violations cv
        WHERE cv.execution_id IN (
            SELECT execution_id FROM agent_audit_trail WHERE agent_audit_trail.customer_id = customer_id
        )
    ) as violations_count,
    CASE
        WHEN AVG(quality_score) > 8.0 AND COUNT(*) FILTER (WHERE pii_detected = true) = 0 THEN 'excellent'
        WHEN AVG(quality_score) > 7.0 AND COUNT(*) FILTER (WHERE pii_detected = true) < 5 THEN 'good'
        WHEN AVG(quality_score) > 6.0 THEN 'fair'
        ELSE 'poor'
    END as health_status
FROM agent_audit_trail
GROUP BY customer_id;

-- View: Real-time compliance dashboard
CREATE OR REPLACE VIEW compliance_dashboard AS
SELECT
    DATE(aat.created_at) as date,
    COUNT(DISTINCT aat.execution_id) as total_executions,
    COUNT(DISTINCT cv.id) as total_violations,
    COUNT(DISTINCT cv.id) FILTER (WHERE cv.severity = 'critical') as critical_violations,
    COUNT(DISTINCT cv.id) FILTER (WHERE cv.resolved = false) as unresolved_violations,
    AVG(aat.compliance_score) as avg_compliance_score,
    COUNT(DISTINCT aat.execution_id) FILTER (WHERE aat.pii_detected = true) as pii_incidents
FROM agent_audit_trail aat
LEFT JOIN compliance_violations cv ON aat.execution_id = cv.execution_id
GROUP BY DATE(aat.created_at)
ORDER BY date DESC;

-- -----------------------------------------------------------------------------
-- SAMPLE DATA: Insert test data for development
-- -----------------------------------------------------------------------------

-- Note: Only insert sample data if tables are empty (for local dev)
INSERT INTO customer_budgets (customer_id, month_year, budget_limit_usd)
SELECT 'client1', TO_CHAR(NOW(), 'YYYY-MM'), 500.00
WHERE NOT EXISTS (SELECT 1 FROM customer_budgets LIMIT 1);

INSERT INTO customer_budgets (customer_id, month_year, budget_limit_usd)
SELECT 'client2', TO_CHAR(NOW(), 'YYYY-MM'), 1000.00
WHERE NOT EXISTS (SELECT 1 FROM customer_budgets WHERE customer_id = 'client2' LIMIT 1);

-- -----------------------------------------------------------------------------
-- GRANTS: Set appropriate permissions
-- -----------------------------------------------------------------------------

-- Grant read-only access to reporting user (if needed)
-- GRANT SELECT ON ALL TABLES IN SCHEMA public TO reporting_user;

-- Grant read/write to application user
-- GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO app_user;

-- -----------------------------------------------------------------------------
-- SCHEMA VERSION
-- -----------------------------------------------------------------------------

COMMENT ON SCHEMA public IS 'Governance schema version 2.0.0';

-- Success message
DO $$
BEGIN
    RAISE NOTICE 'Governance database schema created successfully';
    RAISE NOTICE 'Tables: 11 (audit_trail, quality_metrics, compliance_violations, customer_usage, etc.)';
    RAISE NOTICE 'Views: 3 (daily_governance_summary, customer_health_score, compliance_dashboard)';
    RAISE NOTICE 'Triggers: 3 (auto-update timestamps)';
END $$;
