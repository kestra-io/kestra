# AI Consulting Platform - Technical Specification
## Based on Kestra Open Source with Enterprise Extensions

**Version:** 1.0
**Date:** 2025-11-12
**Target Timeline:** 3 Months to MVP
**Target Market:** Enterprise AI Consulting Clients

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [System Architecture](#system-architecture)
3. [Critical Enhancement: Multi-Worker-Group Support](#critical-enhancement-multi-worker-group-support)
4. [Core Components](#core-components)
5. [Client Isolation & Multi-Tenancy](#client-isolation--multi-tenancy)
6. [Security Architecture](#security-architecture)
7. [Billing & Metering System](#billing--metering-system)
8. [Technology Stack](#technology-stack)
9. [Database Schema](#database-schema)
10. [API Specifications](#api-specifications)
11. [Deployment Architecture](#deployment-architecture)
12. [Monitoring & Observability](#monitoring--observability)
13. [3-Month Implementation Plan](#3-month-implementation-plan)

---

## 1. Executive Summary

### Problem Statement
Kestra OSS is a powerful workflow orchestration platform but lacks:
- **Multi-worker-group support** (enterprise-only feature)
- **Enterprise-grade multi-tenancy** with client isolation
- **Usage-based billing system**
- **Client management portal**
- **Advanced RBAC and SSO**

### Solution
Build an **Enterprise AI Consulting Platform** on top of Kestra OSS by:
1. **Implementing custom multi-worker-group routing** via namespace-based worker isolation
2. **Adding enterprise authentication** (OAuth2/OIDC) layer
3. **Building client management system** for onboarding, quotas, billing
4. **Creating AI-specific plugins** for LLM providers, vector databases, ML ops
5. **Developing white-label client portal** for self-service access

### Success Metrics
- Support **50+ concurrent enterprise clients** on single platform
- **99.9% uptime** SLA compliance
- **$100K+ MRR** within 6 months of launch
- **<5 minute** client onboarding time
- **80% reduction** in manual DevOps work vs. custom solutions

---

## 2. System Architecture

### High-Level Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                       CLIENT LAYER                              │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │Client Portal │  │  Mobile App  │  │  CLI Tool    │        │
│  │(Next.js)     │  │  (Future)    │  │  (Python)    │        │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘        │
│         │                  │                  │                 │
│         └──────────────────┴──────────────────┘                 │
│                            │                                    │
├────────────────────────────┼────────────────────────────────────┤
│                   AUTHENTICATION LAYER                          │
├────────────────────────────┼────────────────────────────────────┤
│                            │                                    │
│                    ┌───────▼────────┐                          │
│                    │  Auth Gateway  │                          │
│                    │  (Kong/Nginx)  │                          │
│                    │  + OAuth2      │                          │
│                    └───────┬────────┘                          │
│                            │                                    │
├────────────────────────────┼────────────────────────────────────┤
│                   APPLICATION LAYER                             │
├────────────────────────────┼────────────────────────────────────┤
│                            │                                    │
│  ┌─────────────────────────▼──────────────────────────┐        │
│  │          Platform Management Service                │        │
│  │         (Custom FastAPI Application)               │        │
│  │                                                     │        │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────┐│        │
│  │  │Client Mgmt   │  │Billing Engine│  │ RBAC     ││        │
│  │  │Service       │  │              │  │ Service  ││        │
│  │  └──────────────┘  └──────────────┘  └──────────┘│        │
│  │                                                     │        │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────┐│        │
│  │  │Worker Group  │  │Quota Manager │  │Analytics ││        │
│  │  │Orchestrator  │  │              │  │ Engine   ││        │
│  │  └──────────────┘  └──────────────┘  └──────────┘│        │
│  └─────────────────────────┬───────────────────────── ┘        │
│                            │                                    │
├────────────────────────────┼────────────────────────────────────┤
│                    KESTRA CORE LAYER                            │
├────────────────────────────┼────────────────────────────────────┤
│                            │                                    │
│  ┌─────────────────────────▼──────────────────────────┐        │
│  │          Kestra API Server (Modified)              │        │
│  │          - Custom Worker Router                    │        │
│  │          - Namespace-based Isolation              │        │
│  │          - Enhanced Logging                        │        │
│  └─────────────────────────┬───────────────────────────┘        │
│                            │                                    │
│  ┌─────────────────────────▼──────────────────────────┐        │
│  │            Kestra Scheduler & Executor             │        │
│  └─────────────────────────┬───────────────────────────┘        │
│                            │                                    │
├────────────────────────────┼────────────────────────────────────┤
│                    WORKER LAYER (Multi-Group)                   │
├────────────────────────────┼────────────────────────────────────┤
│                            │                                    │
│  ┌────────────────────────────────────────────────────────┐    │
│  │              Worker Group Manager                      │    │
│  │        (Custom Service - KEY INNOVATION)              │    │
│  └────────────────────────┬───────────────────────────────┘    │
│                            │                                    │
│         ┌──────────────────┼──────────────────┐                │
│         │                  │                  │                 │
│  ┌──────▼──────┐  ┌────────▼───────┐  ┌──────▼──────┐         │
│  │Worker Group │  │ Worker Group   │  │Worker Group │         │
│  │  CLIENT-1   │  │   CLIENT-2     │  │   SHARED    │         │
│  │             │  │                │  │             │         │
│  │CPU Workers  │  │ GPU Workers    │  │CPU Workers  │         │
│  │(3 pods)     │  │ (2 pods+GPU)   │  │(5 pods)     │         │
│  │             │  │                │  │             │         │
│  │Namespace:   │  │ Namespace:     │  │Namespace:   │         │
│  │client1.*    │  │ client2.*      │  │shared.*     │         │
│  └─────────────┘  └────────────────┘  └─────────────┘         │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                    DATA LAYER                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │PostgreSQL│  │  Redis   │  │  S3/GCS  │  │ ClickHouse│      │
│  │(Metadata)│  │ (Cache)  │  │(Storage) │  │(Analytics)│      │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Critical Enhancement: Multi-Worker-Group Support

### Problem: Kestra OSS Limitation
**Kestra OSS only supports a single worker group**, meaning:
- All workflows from all namespaces share the same worker pool
- No client isolation at compute level
- Can't assign GPU workers to specific clients
- No resource guarantees per client
- Security concern: client code runs on shared workers

**Kestra Enterprise** has worker groups, but:
- Proprietary and expensive
- Requires enterprise license
- Can't customize to our needs

### Solution: Custom Worker Group Router

We'll implement a **Worker Group Orchestrator** that intercepts task assignments and routes them to namespace-specific worker pools.

#### Architecture

```java
// File: platform-services/src/main/java/io/kestra/platform/WorkerGroupRouter.java

package io.kestra.platform;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.WorkerTask;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Custom Worker Group Router for Multi-Tenant Isolation
 *
 * Routes tasks to namespace-specific worker groups by:
 * 1. Intercepting task queue assignments
 * 2. Determining target worker group from namespace
 * 3. Publishing to worker-group-specific queue
 */
@Slf4j
@Singleton
@Replaces(WorkerTaskQueue.class)
public class WorkerGroupRouter {

    private final Map<String, WorkerGroupConfig> workerGroups;
    private final QueueInterface<WorkerTask> baseQueue;
    private final ClientConfigService clientConfigService;

    public WorkerGroupRouter(
        QueueInterface<WorkerTask> baseQueue,
        ClientConfigService clientConfigService
    ) {
        this.baseQueue = baseQueue;
        this.clientConfigService = clientConfigService;
        this.workerGroups = new HashMap<>();
    }

    /**
     * Route task to appropriate worker group based on namespace
     */
    public void routeTask(WorkerTask workerTask) {
        String namespace = workerTask.getTaskRun().getNamespace();

        // Determine worker group
        WorkerGroupConfig workerGroup = determineWorkerGroup(namespace);

        if (workerGroup == null) {
            // Use default shared worker group
            baseQueue.emit(workerTask);
            log.debug("Routed task from namespace {} to default worker group", namespace);
        } else {
            // Route to specific worker group queue
            String queueName = "workergroup-" + workerGroup.getName();
            QueueInterface<WorkerTask> targetQueue = getQueueForWorkerGroup(queueName);
            targetQueue.emit(workerTask);

            log.info("Routed task from namespace {} to worker group {}",
                namespace, workerGroup.getName());

            // Track metrics
            trackWorkerGroupAssignment(namespace, workerGroup.getName());
        }
    }

    /**
     * Determine worker group based on namespace pattern matching
     */
    private WorkerGroupConfig determineWorkerGroup(String namespace) {
        // Check for client-specific worker groups
        for (WorkerGroupConfig config : workerGroups.values()) {
            for (Pattern pattern : config.getNamespacePatterns()) {
                if (pattern.matcher(namespace).matches()) {
                    return config;
                }
            }
        }

        // Check database for dynamic client configurations
        return clientConfigService.getWorkerGroupForNamespace(namespace);
    }

    /**
     * Register a new worker group
     */
    public void registerWorkerGroup(WorkerGroupConfig config) {
        workerGroups.put(config.getName(), config);
        log.info("Registered worker group: {} for namespaces: {}",
            config.getName(), config.getNamespacePatterns());
    }

    private QueueInterface<WorkerTask> getQueueForWorkerGroup(String queueName) {
        // Get or create queue for worker group
        // Implementation depends on queue backend (Kafka, RabbitMQ, etc.)
        return queueFactory.getQueue(queueName);
    }

    private void trackWorkerGroupAssignment(String namespace, String workerGroup) {
        // Send metrics to monitoring system
        metricsService.incrementCounter(
            "worker_group.assignments",
            "namespace", namespace,
            "worker_group", workerGroup
        );
    }
}

/**
 * Worker Group Configuration
 */
@Data
@Builder
public class WorkerGroupConfig {
    private String name;
    private List<Pattern> namespacePatterns;
    private ResourceRequirements resources;
    private Map<String, String> labels;
    private boolean gpuEnabled;
    private int maxConcurrentTasks;
}

/**
 * Resource requirements for worker group
 */
@Data
public class ResourceRequirements {
    private String cpu;           // e.g., "2000m"
    private String memory;        // e.g., "4Gi"
    private String gpu;           // e.g., "nvidia.com/gpu=1"
    private String storageClass;  // e.g., "fast-ssd"
}
```

#### Worker Configuration (Kubernetes)

Each client gets dedicated worker pods that only consume tasks from their worker group:

```yaml
# k8s/worker-groups/client1-workers.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kestra-worker-client1
  namespace: kestra
  labels:
    app: kestra-worker
    client: client1
    worker-group: client1
spec:
  replicas: 3
  selector:
    matchLabels:
      app: kestra-worker
      worker-group: client1
  template:
    metadata:
      labels:
        app: kestra-worker
        worker-group: client1
        client: client1
    spec:
      serviceAccountName: kestra-worker
      containers:
      - name: worker
        image: kestra/kestra:latest
        command:
          - /bin/bash
          - -c
          - |
            # Start Kestra worker with custom configuration
            /app/kestra server worker \
              --worker-group=client1 \
              --namespace-filter='^enterprise\.client1\..*'
        env:
          # Worker group identifier
          - name: KESTRA_WORKER_GROUP
            value: "client1"

          # Only consume from client1 queue
          - name: KESTRA_QUEUE_TYPE
            value: "kafka"
          - name: KESTRA_KAFKA_CONSUMER_TOPIC
            value: "workergroup-client1"

          # Resource limits
          - name: KESTRA_WORKER_THREAD_COUNT
            value: "4"

          # Database connection
          - name: KESTRA_DATASOURCES_POSTGRES_URL
            valueFrom:
              secretKeyRef:
                name: kestra-postgres
                key: url

          # Client-specific secrets (isolated)
          - name: AWS_ACCESS_KEY_ID
            valueFrom:
              secretKeyRef:
                name: client1-secrets
                key: aws_access_key

          - name: OPENAI_API_KEY
            valueFrom:
              secretKeyRef:
                name: client1-secrets
                key: openai_api_key

        resources:
          requests:
            cpu: "1000m"
            memory: "2Gi"
          limits:
            cpu: "2000m"
            memory: "4Gi"

        # Security context for isolation
        securityContext:
          runAsNonRoot: true
          runAsUser: 1000
          allowPrivilegeEscalation: false
          capabilities:
            drop:
              - ALL

---
# k8s/worker-groups/client2-workers.yaml (GPU-enabled)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kestra-worker-client2-gpu
  namespace: kestra
spec:
  replicas: 2
  selector:
    matchLabels:
      worker-group: client2-gpu
  template:
    metadata:
      labels:
        worker-group: client2-gpu
    spec:
      containers:
      - name: worker
        image: kestra/kestra:latest
        command:
          - /app/kestra server worker
          - --worker-group=client2-gpu
          - --namespace-filter='^enterprise\.client2\..*'
        env:
          - name: KESTRA_WORKER_GROUP
            value: "client2-gpu"
          - name: KESTRA_KAFKA_CONSUMER_TOPIC
            value: "workergroup-client2-gpu"
        resources:
          limits:
            nvidia.com/gpu: 1  # Request GPU
            cpu: "4000m"
            memory: "16Gi"

      nodeSelector:
        accelerator: nvidia-tesla-t4  # Deploy on GPU nodes

      tolerations:
      - key: nvidia.com/gpu
        operator: Exists
        effect: NoSchedule
```

#### Database Schema for Worker Groups

```sql
-- Worker group configurations table
CREATE TABLE worker_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    resource_cpu VARCHAR(50),
    resource_memory VARCHAR(50),
    resource_gpu VARCHAR(50),
    max_concurrent_tasks INT DEFAULT 100,
    gpu_enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Namespace to worker group mappings
CREATE TABLE namespace_worker_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    namespace_pattern VARCHAR(255) NOT NULL,
    worker_group_id UUID REFERENCES worker_groups(id) ON DELETE CASCADE,
    priority INT DEFAULT 0,  -- Higher priority patterns matched first
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(namespace_pattern, worker_group_id)
);

-- Client to worker group assignments
CREATE TABLE client_worker_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id) ON DELETE CASCADE,
    worker_group_id UUID REFERENCES worker_groups(id) ON DELETE CASCADE,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(client_id, worker_group_id)
);

-- Worker group metrics (for autoscaling decisions)
CREATE TABLE worker_group_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_group_id UUID REFERENCES worker_groups(id) ON DELETE CASCADE,
    timestamp TIMESTAMP NOT NULL,
    active_workers INT NOT NULL,
    queued_tasks INT NOT NULL,
    running_tasks INT NOT NULL,
    avg_task_duration_seconds FLOAT,
    cpu_utilization_percent FLOAT,
    memory_utilization_percent FLOAT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_worker_group_metrics_timestamp
    ON worker_group_metrics(worker_group_id, timestamp DESC);
```

#### Configuration Service

```java
// platform-services/src/main/java/io/kestra/platform/ClientConfigService.java

@Singleton
public class ClientConfigService {

    @Inject
    private JdbcTemplate jdbcTemplate;

    @Inject
    private WorkerGroupRouter workerGroupRouter;

    @Cacheable("worker-group-mappings")
    public WorkerGroupConfig getWorkerGroupForNamespace(String namespace) {
        String sql = """
            SELECT wg.*
            FROM worker_groups wg
            JOIN namespace_worker_groups nwg ON wg.id = nwg.worker_group_id
            WHERE ? ~ nwg.namespace_pattern
            ORDER BY nwg.priority DESC
            LIMIT 1
            """;

        return jdbcTemplate.queryForObject(
            sql,
            new Object[]{namespace},
            new WorkerGroupConfigRowMapper()
        );
    }

    /**
     * Create dedicated worker group for a client
     */
    @Transactional
    public WorkerGroupConfig createClientWorkerGroup(
        UUID clientId,
        String clientName,
        boolean requiresGPU
    ) {
        String workerGroupName = "client-" + clientName.toLowerCase();

        // Create worker group
        WorkerGroupConfig config = WorkerGroupConfig.builder()
            .name(workerGroupName)
            .gpuEnabled(requiresGPU)
            .maxConcurrentTasks(100)
            .resources(ResourceRequirements.builder()
                .cpu(requiresGPU ? "4000m" : "2000m")
                .memory(requiresGPU ? "16Gi" : "4Gi")
                .gpu(requiresGPU ? "nvidia.com/gpu=1" : null)
                .build())
            .build();

        // Save to database
        UUID workerGroupId = jdbcTemplate.queryForObject(
            "INSERT INTO worker_groups (name, resource_cpu, resource_memory, resource_gpu, gpu_enabled) " +
            "VALUES (?, ?, ?, ?, ?) RETURNING id",
            UUID.class,
            workerGroupName,
            config.getResources().getCpu(),
            config.getResources().getMemory(),
            config.getResources().getGpu(),
            requiresGPU
        );

        // Create namespace mapping (all client namespaces route to this worker group)
        jdbcTemplate.update(
            "INSERT INTO namespace_worker_groups (namespace_pattern, worker_group_id, priority) " +
            "VALUES (?, ?, ?)",
            "^enterprise\\." + clientName + "\\..*",
            workerGroupId,
            100  // High priority
        );

        // Link to client
        jdbcTemplate.update(
            "INSERT INTO client_worker_groups (client_id, worker_group_id, is_default) " +
            "VALUES (?, ?, ?)",
            clientId,
            workerGroupId,
            true
        );

        // Register with router
        workerGroupRouter.registerWorkerGroup(config);

        log.info("Created worker group {} for client {}", workerGroupName, clientName);

        // Trigger Kubernetes deployment
        deployWorkerGroup(config);

        return config;
    }

    /**
     * Deploy worker group to Kubernetes
     */
    private void deployWorkerGroup(WorkerGroupConfig config) {
        // Use Kubernetes Java client to create Deployment
        // Or call Terraform/Helm via ProcessBuilder

        String helmCommand = String.format(
            "helm upgrade --install kestra-worker-%s ./charts/kestra-worker " +
            "--set workerGroup.name=%s " +
            "--set workerGroup.replicas=3 " +
            "--set resources.cpu=%s " +
            "--set resources.memory=%s " +
            "--set gpuEnabled=%s",
            config.getName(),
            config.getName(),
            config.getResources().getCpu(),
            config.getResources().getMemory(),
            config.isGpuEnabled()
        );

        // Execute deployment
        processExecutor.execute(helmCommand);
    }
}
```

### Benefits of This Approach

✅ **Client Isolation**: Each client's workloads run on dedicated worker pods
✅ **Resource Guarantees**: GPU/CPU allocated per client
✅ **Security**: Client secrets isolated, no cross-contamination
✅ **Flexibility**: Can scale worker groups independently
✅ **Cost Attribution**: Track resource usage per client
✅ **Open Source**: Built on Kestra OSS, no enterprise license required

---

## 4. Core Components

### 4.1 Platform Management Service (FastAPI)

```python
# platform-services/app/main.py

from fastapi import FastAPI, Depends, HTTPException
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.orm import Session
from typing import List
import uuid

app = FastAPI(title="AI Consulting Platform API", version="1.0.0")

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token")

# Client Management Endpoints

@app.post("/api/v1/clients", response_model=ClientResponse)
async def create_client(
    client: ClientCreate,
    current_user: User = Depends(get_current_admin_user),
    db: Session = Depends(get_db)
):
    """
    Create a new enterprise client with isolated namespace and worker group
    """
    # Create client record
    db_client = Client(
        id=uuid.uuid4(),
        name=client.name,
        namespace=f"enterprise.{client.name.lower()}",
        tier=client.tier,
        status="active"
    )
    db.add(db_client)

    # Create namespace in Kestra
    await kestra_client.create_namespace(db_client.namespace)

    # Create dedicated worker group if premium tier
    if client.tier in ["professional", "enterprise"]:
        worker_group_config = WorkerGroupService.create_worker_group(
            client_id=db_client.id,
            client_name=client.name,
            requires_gpu=client.requires_gpu
        )

    # Set up resource quotas
    quota = ResourceQuota(
        client_id=db_client.id,
        max_executions_per_month=client.tier_limits.executions,
        max_concurrent_workflows=client.tier_limits.concurrent,
        max_storage_gb=client.tier_limits.storage
    )
    db.add(quota)

    # Initialize billing
    billing_account = BillingAccount(
        client_id=db_client.id,
        stripe_customer_id=await stripe.create_customer(client.name, client.email),
        subscription_tier=client.tier
    )
    db.add(billing_account)

    db.commit()

    # Send welcome email with credentials
    await email_service.send_welcome_email(db_client)

    return ClientResponse.from_orm(db_client)

@app.get("/api/v1/clients/{client_id}/usage")
async def get_client_usage(
    client_id: uuid.UUID,
    period: str = "current_month",
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Get usage metrics and billing information for a client
    """
    # Verify access
    if not (current_user.is_admin or current_user.client_id == client_id):
        raise HTTPException(status_code=403, detail="Not authorized")

    # Query execution metrics from ClickHouse
    usage_data = await analytics_service.get_client_usage(
        client_id=client_id,
        period=period
    )

    # Calculate costs
    cost_breakdown = billing_service.calculate_costs(usage_data)

    return {
        "client_id": client_id,
        "period": period,
        "usage": usage_data,
        "costs": cost_breakdown,
        "quota_remaining": await quota_service.get_remaining_quota(client_id)
    }

@app.post("/api/v1/workflows/deploy")
async def deploy_workflow(
    workflow: WorkflowCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Deploy a workflow to client's namespace
    """
    # Ensure workflow is deployed to correct namespace
    if not workflow.namespace.startswith(f"enterprise.{current_user.client.name}"):
        raise HTTPException(
            status_code=403,
            detail="Can only deploy to your client namespace"
        )

    # Validate workflow YAML
    validation_result = await kestra_client.validate_workflow(workflow.yaml_content)
    if not validation_result.is_valid:
        raise HTTPException(status_code=400, detail=validation_result.errors)

    # Check quota
    quota_ok = await quota_service.check_workflow_quota(current_user.client_id)
    if not quota_ok:
        raise HTTPException(status_code=429, detail="Workflow quota exceeded")

    # Deploy to Kestra
    deployed_workflow = await kestra_client.create_or_update_flow(
        namespace=workflow.namespace,
        flow_id=workflow.id,
        content=workflow.yaml_content
    )

    # Track in our database
    db_workflow = Workflow(
        id=deployed_workflow.id,
        client_id=current_user.client_id,
        namespace=workflow.namespace,
        name=workflow.name,
        version=deployed_workflow.revision
    )
    db.add(db_workflow)
    db.commit()

    return {"status": "deployed", "workflow": deployed_workflow}
```

### 4.2 Quota Management System

```python
# platform-services/app/services/quota_service.py

from datetime import datetime, timedelta
from sqlalchemy import func

class QuotaService:
    def __init__(self, db: Session, redis_client: Redis):
        self.db = db
        self.redis = redis_client

    async def check_execution_quota(self, client_id: uuid.UUID) -> bool:
        """
        Check if client can execute another workflow
        Uses Redis for real-time quota tracking
        """
        quota_key = f"quota:executions:{client_id}:{datetime.now().strftime('%Y-%m')}"

        # Get current usage from Redis
        current_usage = await self.redis.get(quota_key)
        current_usage = int(current_usage) if current_usage else 0

        # Get quota limit from database
        quota = self.db.query(ResourceQuota).filter(
            ResourceQuota.client_id == client_id
        ).first()

        if not quota:
            return False

        if current_usage >= quota.max_executions_per_month:
            # Trigger quota exceeded notification
            await self.notify_quota_exceeded(client_id, "executions")
            return False

        return True

    async def increment_execution_count(self, client_id: uuid.UUID):
        """Increment execution counter (called after successful execution)"""
        quota_key = f"quota:executions:{client_id}:{datetime.now().strftime('%Y-%m')}"

        # Increment with expiry
        pipe = self.redis.pipeline()
        pipe.incr(quota_key)
        pipe.expire(quota_key, 60 * 60 * 24 * 32)  # Expire after 32 days
        await pipe.execute()

    async def check_concurrent_limit(self, client_id: uuid.UUID) -> bool:
        """Check if client can start another concurrent workflow"""
        # Count currently running executions
        running_count = self.db.query(func.count(Execution.id)).filter(
            Execution.client_id == client_id,
            Execution.state.in_(["RUNNING", "PAUSED"])
        ).scalar()

        quota = self.db.query(ResourceQuota).filter(
            ResourceQuota.client_id == client_id
        ).first()

        return running_count < quota.max_concurrent_workflows

    async def notify_quota_exceeded(self, client_id: uuid.UUID, quota_type: str):
        """Send notification when quota is exceeded"""
        client = self.db.query(Client).get(client_id)

        # Send email
        await email_service.send_template(
            to=client.email,
            template="quota_exceeded",
            data={
                "client_name": client.name,
                "quota_type": quota_type,
                "upgrade_url": f"{settings.APP_URL}/upgrade"
            }
        )

        # Send Slack notification to internal team
        await slack_service.send_message(
            channel="#quota-alerts",
            text=f"⚠️ Client {client.name} exceeded {quota_type} quota"
        )
```

---

## 5. Client Isolation & Multi-Tenancy

### Namespace Strategy

```
platform/
├── enterprise.client1.rag/           # Client 1 RAG workflows
├── enterprise.client1.ml/            # Client 1 ML workflows
├── enterprise.client1.etl/           # Client 1 ETL workflows
├── enterprise.client2.content/       # Client 2 content workflows
├── enterprise.client2.analytics/     # Client 2 analytics
├── platform.analytics/               # Platform-wide analytics
├── platform.monitoring/              # Internal monitoring
└── shared.templates/                 # Shared workflow templates
```

### Secret Isolation

Each client gets isolated secret scope:

```yaml
# Kubernetes secrets per client
apiVersion: v1
kind: Secret
metadata:
  name: client1-secrets
  namespace: kestra
type: Opaque
data:
  aws_access_key: <base64>
  openai_api_key: <base64>
  anthropic_api_key: <base64>
  # ... client-specific secrets

---
# RBAC: Workflows can only access their client's secrets
apiVersion: v1
kind: ServiceAccount
metadata:
  name: kestra-worker-client1
  namespace: kestra
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: client1-secret-reader
  namespace: kestra
rules:
- apiGroups: [""]
  resources: ["secrets"]
  resourceNames: ["client1-secrets"]
  verbs: ["get"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: client1-secret-binding
  namespace: kestra
subjects:
- kind: ServiceAccount
  name: kestra-worker-client1
roleRef:
  kind: Role
  name: client1-secret-reader
  apiGroup: rbac.authorization.k8s.io
```

---

## 6. Security Architecture

### OAuth2/OIDC Integration

```typescript
// client-portal/src/lib/auth.ts

import { AuthOptions } from "next-auth"
import Auth0Provider from "next-auth/providers/auth0"

export const authOptions: AuthOptions = {
  providers: [
    Auth0Provider({
      clientId: process.env.AUTH0_CLIENT_ID!,
      clientSecret: process.env.AUTH0_CLIENT_SECRET!,
      issuer: process.env.AUTH0_ISSUER_BASE_URL,
      authorization: {
        params: {
          scope: "openid profile email offline_access",
          audience: "https://platform.aiagency.com/api",
        },
      },
    }),
  ],
  callbacks: {
    async jwt({ token, user, account }) {
      if (account && user) {
        // Fetch user's client assignment
        const client = await fetchUserClient(user.email)
        token.clientId = client.id
        token.role = user.role
        token.accessToken = account.access_token
      }
      return token
    },
    async session({ session, token }) {
      session.user.clientId = token.clientId as string
      session.user.role = token.role as string
      session.accessToken = token.accessToken as string
      return session
    },
  },
  pages: {
    signIn: "/auth/signin",
    error: "/auth/error",
  },
}
```

### API Security Middleware

```python
# platform-services/app/middleware/security.py

from fastapi import Request, HTTPException
from jose import JWTError, jwt
import httpx

async def verify_token(request: Request, call_next):
    """Verify JWT token from Auth0"""
    auth_header = request.headers.get("Authorization")

    if not auth_header or not auth_header.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing or invalid token")

    token = auth_header.split(" ")[1]

    try:
        # Verify with Auth0
        jwks_url = f"https://{settings.AUTH0_DOMAIN}/.well-known/jwks.json"
        async with httpx.AsyncClient() as client:
            jwks = await client.get(jwks_url)

        payload = jwt.decode(
            token,
            jwks.json(),
            algorithms=["RS256"],
            audience=settings.AUTH0_AUDIENCE,
            issuer=f"https://{settings.AUTH0_DOMAIN}/"
        )

        # Attach user info to request
        request.state.user = payload

    except JWTError as e:
        raise HTTPException(status_code=401, detail=f"Invalid token: {str(e)}")

    return await call_next(request)

async def check_client_access(request: Request, call_next):
    """Ensure user can only access their client's resources"""
    user = request.state.user

    # Extract client_id from path (e.g., /api/v1/clients/{client_id}/...)
    path_parts = request.url.path.split("/")
    if "clients" in path_parts:
        idx = path_parts.index("clients")
        if idx + 1 < len(path_parts):
            requested_client_id = path_parts[idx + 1]

            # Allow admins to access any client
            if user.get("role") != "admin":
                if user.get("client_id") != requested_client_id:
                    raise HTTPException(
                        status_code=403,
                        detail="Not authorized to access this client"
                    )

    return await call_next(request)
```

---

## 7. Billing & Metering System

### Usage Tracking

```python
# platform-services/app/services/metering_service.py

from clickhouse_driver import Client as ClickHouseClient
from decimal import Decimal

class MeteringService:
    def __init__(self):
        self.clickhouse = ClickHouseClient(
            host=settings.CLICKHOUSE_HOST,
            database='platform_metrics'
        )

    def record_execution(
        self,
        client_id: uuid.UUID,
        execution_id: str,
        workflow_id: str,
        duration_seconds: float,
        worker_group: str,
        resource_usage: dict
    ):
        """Record execution metrics for billing"""
        self.clickhouse.execute(
            """
            INSERT INTO execution_metrics (
                client_id, execution_id, workflow_id, timestamp,
                duration_seconds, worker_group,
                cpu_seconds, memory_gb_seconds, gpu_seconds,
                storage_gb, egress_gb
            ) VALUES
            """,
            [{
                'client_id': str(client_id),
                'execution_id': execution_id,
                'workflow_id': workflow_id,
                'timestamp': datetime.now(),
                'duration_seconds': duration_seconds,
                'worker_group': worker_group,
                'cpu_seconds': resource_usage.get('cpu_seconds', 0),
                'memory_gb_seconds': resource_usage.get('memory_gb_seconds', 0),
                'gpu_seconds': resource_usage.get('gpu_seconds', 0),
                'storage_gb': resource_usage.get('storage_gb', 0),
                'egress_gb': resource_usage.get('egress_gb', 0)
            }]
        )

    def calculate_monthly_bill(self, client_id: uuid.UUID, year: int, month: int) -> dict:
        """Calculate bill for a client for given month"""

        # Fetch usage metrics
        query = """
            SELECT
                COUNT(*) as total_executions,
                SUM(duration_seconds) as total_duration_seconds,
                SUM(cpu_seconds) as total_cpu_seconds,
                SUM(memory_gb_seconds) as total_memory_gb_seconds,
                SUM(gpu_seconds) as total_gpu_seconds,
                SUM(storage_gb) as total_storage_gb,
                SUM(egress_gb) as total_egress_gb
            FROM execution_metrics
            WHERE client_id = %(client_id)s
              AND toYear(timestamp) = %(year)s
              AND toMonth(timestamp) = %(month)s
        """

        result = self.clickhouse.execute(
            query,
            {'client_id': str(client_id), 'year': year, 'month': month}
        )[0]

        # Pricing (example rates)
        RATE_PER_EXECUTION = Decimal('0.01')
        RATE_PER_CPU_HOUR = Decimal('0.05')
        RATE_PER_GB_MEMORY_HOUR = Decimal('0.01')
        RATE_PER_GPU_HOUR = Decimal('1.50')
        RATE_PER_GB_STORAGE = Decimal('0.023')
        RATE_PER_GB_EGRESS = Decimal('0.09')

        # Calculate costs
        execution_cost = Decimal(result[0]) * RATE_PER_EXECUTION
        cpu_cost = Decimal(result[2]) / 3600 * RATE_PER_CPU_HOUR
        memory_cost = Decimal(result[3]) / 3600 * RATE_PER_GB_MEMORY_HOUR
        gpu_cost = Decimal(result[4]) / 3600 * RATE_PER_GPU_HOUR
        storage_cost = Decimal(result[5]) * RATE_PER_GB_STORAGE
        egress_cost = Decimal(result[6]) * RATE_PER_GB_EGRESS

        total_cost = (
            execution_cost + cpu_cost + memory_cost +
            gpu_cost + storage_cost + egress_cost
        )

        return {
            'client_id': client_id,
            'period': f'{year}-{month:02d}',
            'usage': {
                'executions': result[0],
                'cpu_hours': float(Decimal(result[2]) / 3600),
                'memory_gb_hours': float(Decimal(result[3]) / 3600),
                'gpu_hours': float(Decimal(result[4]) / 3600),
                'storage_gb': float(result[5]),
                'egress_gb': float(result[6])
            },
            'costs': {
                'executions': float(execution_cost),
                'compute': float(cpu_cost + memory_cost),
                'gpu': float(gpu_cost),
                'storage': float(storage_cost),
                'egress': float(egress_cost),
                'total': float(total_cost)
            }
        }
```

### Stripe Integration

```python
# platform-services/app/services/billing_service.py

import stripe
from datetime import datetime

stripe.api_key = settings.STRIPE_SECRET_KEY

class BillingService:
    async def create_invoice(self, client_id: uuid.UUID, year: int, month: int):
        """Generate and send Stripe invoice"""

        # Calculate usage
        metering = MeteringService()
        bill_data = metering.calculate_monthly_bill(client_id, year, month)

        # Get client billing account
        billing_account = db.query(BillingAccount).filter(
            BillingAccount.client_id == client_id
        ).first()

        # Create Stripe invoice
        invoice = stripe.Invoice.create(
            customer=billing_account.stripe_customer_id,
            auto_advance=True,
            collection_method='charge_automatically',
            metadata={
                'client_id': str(client_id),
                'period': bill_data['period']
            }
        )

        # Add line items
        stripe.InvoiceItem.create(
            customer=billing_account.stripe_customer_id,
            invoice=invoice.id,
            amount=int(bill_data['costs']['executions'] * 100),
            currency='usd',
            description=f"Workflow Executions ({bill_data['usage']['executions']})"
        )

        stripe.InvoiceItem.create(
            customer=billing_account.stripe_customer_id,
            invoice=invoice.id,
            amount=int(bill_data['costs']['compute'] * 100),
            currency='usd',
            description=f"Compute ({bill_data['usage']['cpu_hours']:.2f} CPU hours)"
        )

        if bill_data['costs']['gpu'] > 0:
            stripe.InvoiceItem.create(
                customer=billing_account.stripe_customer_id,
                invoice=invoice.id,
                amount=int(bill_data['costs']['gpu'] * 100),
                currency='usd',
                description=f"GPU ({bill_data['usage']['gpu_hours']:.2f} GPU hours)"
            )

        # Finalize invoice
        stripe.Invoice.finalize_invoice(invoice.id)

        # Store in our database
        db_invoice = Invoice(
            id=uuid.uuid4(),
            client_id=client_id,
            stripe_invoice_id=invoice.id,
            period_year=year,
            period_month=month,
            amount_cents=invoice.total,
            status='pending',
            usage_data=bill_data
        )
        db.add(db_invoice)
        db.commit()

        return invoice
```

---

## 8. Technology Stack

### Backend
- **Kestra Core**: Java 21 + Micronaut (OSS base)
- **Platform API**: Python 3.11 + FastAPI
- **Databases**:
  - PostgreSQL 15 (metadata, clients, billing)
  - ClickHouse (analytics, usage metrics)
  - Redis (caching, rate limiting, real-time quotas)
- **Message Queue**: Kafka (worker group routing)
- **Object Storage**: S3 / Google Cloud Storage

### Frontend
- **Client Portal**: Next.js 14 + TypeScript + Tailwind CSS
- **Admin Panel**: React + TypeScript + Shadcn/UI
- **Authentication**: Auth0 / AWS Cognito

### Infrastructure
- **Container Orchestration**: Kubernetes (EKS / GKE)
- **Service Mesh**: Istio (optional, for advanced traffic management)
- **Monitoring**: Prometheus + Grafana + Loki
- **Tracing**: OpenTelemetry + Jaeger
- **CI/CD**: GitHub Actions + ArgoCD
- **IaC**: Terraform + Helm

### AI/ML Tools
- **LLM Providers**: OpenAI, Anthropic, Azure OpenAI
- **Vector DBs**: Pinecone, Weaviate, Qdrant
- **ML Ops**: MLflow, Weights & Biases
- **GPU Runtime**: NVIDIA Container Toolkit

---

## 9. Database Schema

### Core Schema (PostgreSQL)

```sql
-- Clients table
CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    namespace VARCHAR(255) NOT NULL UNIQUE,
    tier VARCHAR(50) NOT NULL CHECK (tier IN ('starter', 'professional', 'enterprise')),
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    contact_email VARCHAR(255) NOT NULL,
    contact_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    role VARCHAR(50) NOT NULL CHECK (role IN ('admin', 'developer', 'viewer')),
    auth0_user_id VARCHAR(255) UNIQUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP
);

-- Resource quotas
CREATE TABLE resource_quotas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id) ON DELETE CASCADE UNIQUE,
    max_executions_per_month INT NOT NULL,
    max_concurrent_workflows INT NOT NULL,
    max_storage_gb INT NOT NULL,
    max_cpu_hours_per_month INT,
    max_gpu_hours_per_month INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Workflows (tracking)
CREATE TABLE workflows (
    id VARCHAR(255) PRIMARY KEY,  -- Matches Kestra flow ID
    client_id UUID REFERENCES clients(id) ON DELETE CASCADE,
    namespace VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Billing accounts
CREATE TABLE billing_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id) ON DELETE CASCADE UNIQUE,
    stripe_customer_id VARCHAR(255) UNIQUE,
    subscription_tier VARCHAR(50),
    payment_method_id VARCHAR(255),
    billing_email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Invoices
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id) ON DELETE CASCADE,
    stripe_invoice_id VARCHAR(255) UNIQUE,
    period_year INT NOT NULL,
    period_month INT NOT NULL,
    amount_cents BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    usage_data JSONB,
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(client_id, period_year, period_month)
);

-- Audit log
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id) ON DELETE SET NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(255),
    details JSONB,
    ip_address INET,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_client ON audit_log(client_id, timestamp DESC);
CREATE INDEX idx_audit_log_user ON audit_log(user_id, timestamp DESC);
```

### Analytics Schema (ClickHouse)

```sql
-- Execution metrics table
CREATE TABLE execution_metrics (
    client_id UUID,
    execution_id String,
    workflow_id String,
    namespace String,
    timestamp DateTime,
    duration_seconds Float64,
    status String,
    worker_group String,
    cpu_seconds Float64,
    memory_gb_seconds Float64,
    gpu_seconds Float64,
    storage_gb Float64,
    egress_gb Float64,
    error_message String
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (client_id, timestamp);

-- Aggregated daily metrics (materialized view)
CREATE MATERIALIZED VIEW daily_client_metrics
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (client_id, date)
AS SELECT
    client_id,
    toDate(timestamp) AS date,
    COUNT(*) AS total_executions,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS successful_executions,
    SUM(duration_seconds) AS total_duration_seconds,
    SUM(cpu_seconds) AS total_cpu_seconds,
    SUM(memory_gb_seconds) AS total_memory_gb_seconds,
    SUM(gpu_seconds) AS total_gpu_seconds
FROM execution_metrics
GROUP BY client_id, date;
```

---

## 10. API Specifications

### REST API Endpoints

```yaml
# openapi.yaml (excerpt)

openapi: 3.0.0
info:
  title: AI Consulting Platform API
  version: 1.0.0

paths:
  /api/v1/clients:
    post:
      summary: Create new client
      security:
        - bearerAuth: []
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ClientCreate'
      responses:
        '201':
          description: Client created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ClientResponse'

  /api/v1/clients/{clientId}/workflows:
    get:
      summary: List client workflows
      parameters:
        - name: clientId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Workflows list
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Workflow'

  /api/v1/workflows/execute:
    post:
      summary: Execute a workflow
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                workflow_id:
                  type: string
                inputs:
                  type: object
      responses:
        '202':
          description: Execution started
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ExecutionResponse'

components:
  schemas:
    ClientCreate:
      type: object
      required:
        - name
        - tier
        - email
      properties:
        name:
          type: string
        tier:
          type: string
          enum: [starter, professional, enterprise]
        email:
          type: string
        requires_gpu:
          type: boolean
          default: false

    ClientResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        namespace:
          type: string
        tier:
          type: string
        status:
          type: string
        created_at:
          type: string
          format: date-time
```

---

## 11. Deployment Architecture

### Kubernetes Architecture

```yaml
# k8s/production/kestra-deployment.yaml

apiVersion: apps/v1
kind: Deployment
metadata:
  name: kestra-api-server
  namespace: kestra
spec:
  replicas: 3
  selector:
    matchLabels:
      app: kestra-api
  template:
    spec:
      containers:
      - name: kestra
        image: kestra/kestra:latest
        command: ["/app/kestra", "server", "standalone"]
        env:
          - name: KESTRA_CONFIGURATION
            value: |
              micronaut:
                server:
                  port: 8080
              datasources:
                postgres:
                  url: jdbc:postgresql://postgres:5432/kestra
                  username: kestra
                  password: ${POSTGRES_PASSWORD}
              kestra:
                queue:
                  type: kafka
                kafka:
                  client:
                    properties:
                      bootstrap.servers: kafka:9092
                repository:
                  type: postgres
                storage:
                  type: s3
                  s3:
                    bucket: kestra-storage
                    region: us-east-1
        resources:
          requests:
            cpu: "2000m"
            memory: "4Gi"
          limits:
            cpu: "4000m"
            memory: "8Gi"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5

---
apiVersion: v1
kind: Service
metadata:
  name: kestra-api
  namespace: kestra
spec:
  selector:
    app: kestra-api
  ports:
    - protocol: TCP
      port: 8080
      targetPort: 8080
  type: ClusterIP

---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: kestra-ingress
  namespace: kestra
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - platform.aiagency.com
    - api.platform.aiagency.com
    secretName: platform-tls
  rules:
  - host: platform.aiagency.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: client-portal
            port:
              number: 3000
  - host: api.platform.aiagency.com
    http:
      paths:
      - path: /kestra
        pathType: Prefix
        backend:
          service:
            name: kestra-api
            port:
              number: 8080
      - path: /
        pathType: Prefix
        backend:
          service:
            name: platform-api
            port:
              number: 8000
```

### Terraform Infrastructure

```hcl
# terraform/main.tf

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 19.0"

  cluster_name    = "ai-consulting-platform"
  cluster_version = "1.28"

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  eks_managed_node_groups = {
    # CPU workers
    general = {
      min_size     = 3
      max_size     = 10
      desired_size = 5

      instance_types = ["m5.2xlarge"]
      capacity_type  = "ON_DEMAND"

      labels = {
        workload = "general"
      }
    }

    # GPU workers for AI workloads
    gpu = {
      min_size     = 0
      max_size     = 5
      desired_size = 2

      instance_types = ["g4dn.xlarge"]

      ami_type = "AL2_x86_64_GPU"

      labels = {
        workload = "gpu"
        "nvidia.com/gpu" = "true"
      }

      taints = [{
        key    = "nvidia.com/gpu"
        value  = "true"
        effect = "NO_SCHEDULE"
      }]
    }
  }
}

# RDS PostgreSQL
resource "aws_db_instance" "postgres" {
  identifier           = "kestra-postgres"
  engine               = "postgres"
  engine_version       = "15.4"
  instance_class       = "db.r6g.xlarge"
  allocated_storage    = 100
  storage_encrypted    = true

  db_name  = "kestra"
  username = "kestra"
  password = random_password.db_password.result

  vpc_security_group_ids = [aws_security_group.postgres.id]
  db_subnet_group_name   = aws_db_subnet_group.postgres.name

  backup_retention_period = 7
  backup_window          = "03:00-04:00"
  maintenance_window     = "mon:04:00-mon:05:00"

  multi_az = true

  tags = {
    Name = "kestra-postgres"
  }
}

# S3 bucket for workflow storage
resource "aws_s3_bucket" "kestra_storage" {
  bucket = "kestra-storage-${random_id.suffix.hex}"

  tags = {
    Name = "kestra-storage"
  }
}

resource "aws_s3_bucket_versioning" "kestra_storage" {
  bucket = aws_s3_bucket.kestra_storage.id

  versioning_configuration {
    status = "Enabled"
  }
}
```

---

## 12. Monitoring & Observability

### Prometheus Metrics

```yaml
# k8s/monitoring/prometheus-config.yaml

apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: monitoring
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s

    scrape_configs:
      # Kestra metrics
      - job_name: 'kestra'
        kubernetes_sd_configs:
          - role: pod
            namespaces:
              names:
                - kestra
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            regex: kestra.*
            action: keep
          - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
            regex: "true"
            action: keep

      # Platform API metrics
      - job_name: 'platform-api'
        static_configs:
          - targets: ['platform-api:8000']

      # Worker group metrics
      - job_name: 'kestra-workers'
        kubernetes_sd_configs:
          - role: pod
            namespaces:
              names:
                - kestra
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            regex: kestra-worker
            action: keep
          - source_labels: [__meta_kubernetes_pod_label_worker_group]
            target_label: worker_group
```

### Grafana Dashboards

Key dashboards to create:
1. **Platform Overview**: Total clients, executions, revenue
2. **Client Health**: Per-client success rates, SLA compliance
3. **Worker Group Utilization**: CPU/memory/GPU usage per group
4. **Billing Metrics**: Revenue, costs, margins
5. **System Performance**: Kestra API latency, queue depths

---

## 13. 3-Month Implementation Plan

### Month 1: Foundation (Weeks 1-4)

**Week 1: Infrastructure Setup**
- [x] Set up AWS/GCP infrastructure with Terraform
- [x] Deploy Kubernetes cluster (EKS/GKE)
- [x] Set up PostgreSQL, Redis, S3
- [x] Deploy base Kestra installation
- [x] Configure CI/CD pipelines (GitHub Actions + ArgoCD)

**Week 2: Worker Group Implementation**
- [ ] Implement WorkerGroupRouter in Java
- [ ] Create worker group database schema
- [ ] Deploy test worker groups (1 shared, 2 client-specific)
- [ ] Test namespace-based routing
- [ ] Validate worker isolation

**Week 3: Platform API Development**
- [ ] Build FastAPI application structure
- [ ] Implement client management endpoints
- [ ] Add OAuth2/Auth0 integration
- [ ] Create quota management service
- [ ] Build RBAC middleware

**Week 4: Client Portal (MVP)**
- [ ] Set up Next.js project with Auth0
- [ ] Build dashboard (executions, usage metrics)
- [ ] Create workflow listing page
- [ ] Add execution logs viewer
- [ ] Implement basic settings page

### Month 2: Core Features (Weeks 5-8)

**Week 5: Billing System**
- [ ] Implement metering service (ClickHouse integration)
- [ ] Build billing calculation logic
- [ ] Integrate Stripe for invoicing
- [ ] Create invoice generation workflow
- [ ] Add usage alerts

**Week 6: AI Workflow Templates**
- [ ] Port 4 POC workflows to production
- [ ] Create 10 additional AI templates:
  - LLM batch processing
  - RAG pipeline
  - ML model training
  - Data pipeline orchestration
  - API integration workflows
- [ ] Document each template
- [ ] Add template deployment via API

**Week 7: Client Onboarding**
- [ ] Build client creation automation
- [ ] Implement namespace provisioning
- [ ] Auto-deploy worker groups
- [ ] Generate client credentials
- [ ] Create welcome email workflow

**Week 8: Testing & Security**
- [ ] Penetration testing
- [ ] Load testing (100 concurrent workflows)
- [ ] Security audit
- [ ] Fix critical issues
- [ ] Document security procedures

### Month 3: Polish & Launch (Weeks 9-12)

**Week 9: Advanced Features**
- [ ] Add SLA monitoring
- [ ] Build alerting system
- [ ] Implement cost attribution
- [ ] Create analytics dashboard
- [ ] Add audit logging

**Week 10: Beta Testing**
- [ ] Onboard 3 beta clients
- [ ] Run real workloads
- [ ] Gather feedback
- [ ] Fix bugs
- [ ] Optimize performance

**Week 11: Documentation & Training**
- [ ] Write user documentation
- [ ] Create video tutorials
- [ ] Build knowledge base
- [ ] Prepare sales materials
- [ ] Train support team

**Week 12: Launch Preparation**
- [ ] Final security review
- [ ] Performance optimization
- [ ] Set up monitoring alerts
- [ ] Prepare launch announcement
- [ ] **GO LIVE** 🚀

---

## Development Team Allocation

**Required Team (3-month timeline):**
- 1x Backend Engineer (Java/Micronaut) - Worker group implementation
- 1x Backend Engineer (Python/FastAPI) - Platform API
- 1x Frontend Engineer (Next.js) - Client portal
- 1x DevOps Engineer - Infrastructure, K8s, CI/CD
- 0.5x Product Manager - Requirements, testing
- 0.25x Designer - UI/UX for client portal

**Total: ~4.75 FTE**

---

## Success Criteria

### Technical
- ✅ Support 50+ concurrent clients
- ✅ 99.9% uptime SLA
- ✅ <500ms API response time (p95)
- ✅ Worker group isolation validated
- ✅ Zero cross-client data leaks

### Business
- ✅ 5 paying clients by end of Month 3
- ✅ $10K+ MRR within 3 months
- ✅ 10 AI workflow templates available
- ✅ <5 minute client onboarding
- ✅ NPS score >40

---

## Appendix A: Custom Plugin Examples

```java
// plugins/ai-plugins/src/main/java/io/kestra/plugin/ai/anthropic/SendMessage.java

package io.kestra.plugin.ai.anthropic;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import lombok.Builder;
import lombok.Getter;

@Plugin(
    examples = {
        @Example(
            title = "Send a message to Claude API",
            code = {
                "apiKey: \"{{ secret('ANTHROPIC_API_KEY') }}\"",
                "model: claude-3-5-sonnet-20241022",
                "maxTokens: 1024",
                "messages:",
                "  - role: user",
                "    content: Explain quantum computing in simple terms"
            }
        )
    }
)
@Builder
@Getter
public class SendMessage extends Task implements RunnableTask<SendMessage.Output> {
    private String apiKey;
    private String model;
    private Integer maxTokens;
    private List<Message> messages;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String resolvedApiKey = runContext.render(this.apiKey);

        AnthropicClient client = new AnthropicClient(resolvedApiKey);

        MessageResponse response = client.messages().create(
            MessageRequest.builder()
                .model(this.model)
                .maxTokens(this.maxTokens)
                .messages(this.messages)
                .build()
        );

        return Output.builder()
            .content(response.getContent().get(0).getText())
            .usage(response.getUsage())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String content;
        private Usage usage;
    }
}
```

---

## Conclusion

This technical specification provides a complete blueprint for building an enterprise-grade AI consulting platform on top of Kestra OSS within 3 months.

**Key Innovations:**
1. **Custom multi-worker-group routing** - Solves OSS limitation
2. **Namespace-based client isolation** - Enterprise multi-tenancy
3. **Usage-based billing** - Automated revenue tracking
4. **AI-first workflow templates** - Rapid client value delivery

**Next Steps:**
1. Review and approve this specification
2. Provision development infrastructure
3. Assign development team
4. Begin Week 1 implementation

---

**Document Version:** 1.0
**Last Updated:** 2025-11-12
**Author:** AI Consulting Platform Team
