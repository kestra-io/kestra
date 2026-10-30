-- BEFORE keeps the enum order aligned with the H2 and MySQL migrations.
ALTER TYPE state_type ADD VALUE IF NOT EXISTS 'PAUSING' BEFORE 'PAUSED';
