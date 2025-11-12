-- =============================================================================
-- SEED V100: Default Worker Groups and Namespace Mappings
-- =============================================================================
-- Description: Populates default worker groups for initial deployment
-- Author: Kestra Platform Team
-- Date: 2025-11-12
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Seed Worker Groups
-- -----------------------------------------------------------------------------

-- Shared worker pool (default for general workloads)
INSERT INTO worker_groups (
    name,
    description,
    resource_cpu,
    resource_memory,
    resource_gpu,
    max_concurrent_tasks,
    gpu_enabled,
    auto_scaling_enabled,
    min_replicas,
    max_replicas,
    status
) VALUES (
    'shared',
    'Shared worker pool for general workloads. Used as default for platform, demo, and shared namespaces.',
    '2000m',
    '4Gi',
    NULL,
    100,
    FALSE,
    FALSE,
    2,
    5,
    'active'
);

-- Client 1 dedicated CPU workers
INSERT INTO worker_groups (
    name,
    description,
    resource_cpu,
    resource_memory,
    resource_gpu,
    max_concurrent_tasks,
    gpu_enabled,
    auto_scaling_enabled,
    min_replicas,
    max_replicas,
    status
) VALUES (
    'client1-cpu',
    'Dedicated CPU workers for Enterprise Client 1. Isolated execution environment with dedicated resources.',
    '2000m',
    '4Gi',
    NULL,
    50,
    FALSE,
    TRUE,
    2,
    10,
    'active'
);

-- Client 2 GPU-enabled workers
INSERT INTO worker_groups (
    name,
    description,
    resource_cpu,
    resource_memory,
    resource_gpu,
    max_concurrent_tasks,
    gpu_enabled,
    auto_scaling_enabled,
    min_replicas,
    max_replicas,
    status
) VALUES (
    'client2-gpu',
    'GPU-enabled workers for Enterprise Client 2. Optimized for ML training and inference workloads.',
    '4000m',
    '16Gi',
    'nvidia.com/gpu=1',
    20,
    TRUE,
    FALSE,
    1,
    3,
    'active'
);

-- -----------------------------------------------------------------------------
-- Seed Namespace Mappings
-- -----------------------------------------------------------------------------

-- Shared namespaces → shared worker group
INSERT INTO namespace_worker_groups (
    namespace_pattern,
    worker_group_id,
    priority,
    description,
    enabled
) VALUES (
    '^shared\..*$',
    (SELECT id FROM worker_groups WHERE name = 'shared'),
    50,
    'All namespaces starting with "shared." route to shared worker group',
    TRUE
);

INSERT INTO namespace_worker_groups (
    namespace_pattern,
    worker_group_id,
    priority,
    description,
    enabled
) VALUES (
    '^platform\..*$',
    (SELECT id FROM worker_groups WHERE name = 'shared'),
    50,
    'All platform internal workflows route to shared worker group',
    TRUE
);

INSERT INTO namespace_worker_groups (
    namespace_pattern,
    worker_group_id,
    priority,
    description,
    enabled
) VALUES (
    '^demo\..*$',
    (SELECT id FROM worker_groups WHERE name = 'shared'),
    50,
    'Demo and testing workflows route to shared worker group',
    TRUE
);

-- Client 1 namespaces → client1-cpu worker group
INSERT INTO namespace_worker_groups (
    namespace_pattern,
    worker_group_id,
    priority,
    description,
    enabled
) VALUES (
    '^enterprise\.client1\..*$',
    (SELECT id FROM worker_groups WHERE name = 'client1-cpu'),
    100,
    'All Enterprise Client 1 workflows route to dedicated CPU worker group',
    TRUE
);

-- Client 2 namespaces → client2-gpu worker group
INSERT INTO namespace_worker_groups (
    namespace_pattern,
    worker_group_id,
    priority,
    description,
    enabled
) VALUES (
    '^enterprise\.client2\..*$',
    (SELECT id FROM worker_groups WHERE name = 'client2-gpu'),
    100,
    'All Enterprise Client 2 workflows route to GPU-enabled worker group',
    TRUE
);

-- -----------------------------------------------------------------------------
-- Log seeded data
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    wg_count INT;
    mapping_count INT;
BEGIN
    SELECT COUNT(*) INTO wg_count FROM worker_groups;
    SELECT COUNT(*) INTO mapping_count FROM namespace_worker_groups;

    RAISE NOTICE 'Seeded % worker groups', wg_count;
    RAISE NOTICE 'Seeded % namespace mappings', mapping_count;
END $$;

-- =============================================================================
-- END OF SEED V100
-- =============================================================================
