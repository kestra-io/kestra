CREATE INDEX ix_service_instance_state ON service_instance (`state`);
CREATE INDEX ix_service_instance_type_created_at_updated_at ON service_instance (`service_type`, `created_at`, `updated_at`);