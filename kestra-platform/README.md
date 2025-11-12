# Kestra Platform - Multi-Worker-Group Engine

**Version:** 1.0.0 - Phase 1 Complete
**Status:** Production Ready
**Last Updated:** 2025-11-12

---

## 🎯 What This Is

This is a **production-ready multi-worker-group orchestration platform** built on Kestra OSS. It provides:

✅ **Multi-Worker-Group Support** - Route tasks to namespace-specific worker pools (NOT available in Kestra OSS)
✅ **Client Isolation** - Dedicated workers per client with isolated secrets
✅ **GPU Support** - Separate GPU-enabled workers for ML workloads
✅ **Zero Hardcoded Values** - All configuration via environment variables
✅ **Database-Driven** - Worker groups configured via PostgreSQL
✅ **Local Deployment** - Run entire stack with Docker Compose

**This solves the #1 limitation of Kestra OSS:** Single worker group only.

---

## 📦 What's Included

### Phase 1 Deliverables (COMPLETE)

```
kestra-platform/
├── .env.example                    # Configuration template (NO secrets)
├── .gitignore                      # Prevents committing secrets
├── docker-compose.yml              # Full stack deployment
│
├── kestra-extensions/              # Custom Java code
│   ├── build.gradle                # Gradle build config
│   ├── Dockerfile                  # Builds custom Kestra image
│   └── src/main/java/io/kestra/platform/
│       ├── WorkerGroupRouter.java         # Core routing logic
│       ├── WorkerGroupConfig.java         # Configuration model
│       ├── NamespaceMapping.java          # Namespace patterns
│       └── WorkerGroupRepository.java     # Database access
│
├── database/
│   ├── migrations/
│   │   └── V001__create_worker_groups.sql # Schema
│   └── seeds/
│       └── V100__seed_default_worker_groups.sql # Initial data
│
├── workers/                        # Worker group Dockerfiles
│   ├── shared/
│   ├── client1/
│   └── client2-gpu/
│
├── scripts/                        # Management scripts
│   ├── start.sh                    # Start platform
│   ├── stop.sh                     # Stop platform
│   └── test-routing.sh             # Test routing
│
├── test-workflows/                 # Validation workflows
│   ├── test-shared-namespace.yml
│   ├── test-client1-namespace.yml
│   └── test-client2-namespace.yml
│
└── docs/                           # Documentation
    └── README.md                   # This file
```

---

## 🚀 Quick Start (5 Minutes)

### Prerequisites

- Docker & Docker Compose installed
- 8GB+ RAM available
- Ports 8080, 5432, 9092, 6379, 2181 available

### Step 1: Clone and Configure

```bash
# Navigate to the platform directory
cd kestra-platform

# Copy environment template
cp .env.example .env

# Edit configuration (set secure passwords!)
nano .env
```

**IMPORTANT:** Change these values in `.env`:
- `POSTGRES_PASSWORD` - Set a secure password
- `REDIS_PASSWORD` - Set a secure password

### Step 2: Start the Platform

```bash
# Make scripts executable (if not already)
chmod +x scripts/*.sh

# Start everything
./scripts/start.sh
```

This will:
1. Build custom Kestra image with worker group routing
2. Start PostgreSQL, Kafka, Redis, Zookeeper
3. Run database migrations
4. Start Kestra server
5. Start 3 worker groups (shared, client1, client2)

**Wait ~2 minutes** for all services to be healthy.

### Step 3: Access Kestra

Open your browser: **http://localhost:8080**

You should see the Kestra UI.

### Step 4: Test Worker Group Routing

```bash
# Run routing tests
./scripts/test-routing.sh
```

This verifies:
- ✅ Database tables exist
- ✅ Worker groups are configured
- ✅ Namespace mappings are loaded
- ✅ Workers are running

### Step 5: Deploy Test Workflows

In Kestra UI:
1. Go to **Flows** tab
2. Click **Create**
3. Copy content from `test-workflows/test-shared-namespace.yml`
4. Click **Save**
5. Click **Execute**
6. Check logs - should show "executing in SHARED namespace"

Repeat for `test-client1-namespace.yml` and `test-client2-namespace.yml`.

---

## 🏗️ Architecture

### How It Works

```
Workflow Execution Request
         │
         ▼
┌────────────────────┐
│  WorkerGroupRouter │ ◄─── Queries namespace mappings from DB
│  (Java Service)    │
└────────┬───────────┘
         │
         ├──────────────┬──────────────┬────────────────┐
         ▼              ▼              ▼                ▼
   Namespace:      Namespace:     Namespace:      Namespace:
   shared.*        platform.*     enterprise.     enterprise.
                                  client1.*       client2.*
         │              │              │                │
         ▼              ▼              ▼                ▼
   ┌─────────┐    ┌─────────┐   ┌──────────┐    ┌──────────┐
   │ Shared  │    │ Shared  │   │ Client1  │    │ Client2  │
   │ Workers │    │ Workers │   │  Workers │    │  Workers │
   │ (CPU)   │    │ (CPU)   │   │  (CPU)   │    │  (GPU)   │
   └─────────┘    └─────────┘   └──────────┘    └──────────┘
```

### Database Schema

**worker_groups** table:
- Stores worker group configurations (name, resources, limits)

**namespace_worker_groups** table:
- Maps regex patterns to worker groups
- Priority-based matching (higher priority wins)

**worker_group_metrics** table:
- Tracks usage, performance, resource utilization

### Routing Logic

1. Task arrives with namespace (e.g., `enterprise.client1.rag`)
2. `WorkerGroupRouter` queries database for matching pattern
3. Database uses PostgreSQL regex operator (`~`) for efficient matching
4. Returns worker group name (e.g., `client1-cpu`)
5. Task published to Kafka topic: `workergroup-client1-cpu`
6. Only `client1-cpu` workers consume from that topic
7. **Result:** Client isolation achieved ✅

---

## 🔧 Configuration

### Environment Variables (.env file)

All configuration is externalized. **NO hardcoded values anywhere.**

#### Required Variables

```bash
# Database (MUST SET)
POSTGRES_PASSWORD=your-secure-password-here

# Redis (MUST SET)
REDIS_PASSWORD=your-redis-password-here
```

#### Worker Group Configuration

```bash
# Shared workers
WORKER_SHARED_REPLICAS=2
WORKER_SHARED_CPU_LIMIT=2
WORKER_SHARED_MEMORY_LIMIT=4G
WORKER_SHARED_NAMESPACE_FILTER=^(shared|platform|demo)\..*

# Client1 workers
WORKER_CLIENT1_REPLICAS=2
WORKER_CLIENT1_CPU_LIMIT=2
WORKER_CLIENT1_MEMORY_LIMIT=4G
WORKER_CLIENT1_NAMESPACE_FILTER=^enterprise\.client1\..*

# Client2 GPU workers
WORKER_CLIENT2_REPLICAS=1
WORKER_CLIENT2_CPU_LIMIT=4
WORKER_CLIENT2_MEMORY_LIMIT=16G
WORKER_CLIENT2_GPU_ENABLED=true
WORKER_CLIENT2_NAMESPACE_FILTER=^enterprise\.client2\..*
```

#### Client-Specific Secrets

Secrets are **isolated per worker group**:

```bash
# Client 1 secrets (ONLY accessible to client1 workers)
CLIENT1_AWS_ACCESS_KEY_ID=
CLIENT1_AWS_SECRET_ACCESS_KEY=
CLIENT1_OPENAI_API_KEY=

# Client 2 secrets (ONLY accessible to client2 workers)
CLIENT2_ANTHROPIC_API_KEY=
CLIENT2_PINECONE_API_KEY=
```

**Security:** Client1 workers CANNOT access Client2 secrets and vice versa.

---

## 📊 Worker Groups

### Default Configuration

| Worker Group | Namespaces | CPUs | Memory | GPU | Replicas |
|--------------|------------|------|--------|-----|----------|
| **shared** | `shared.*`, `platform.*`, `demo.*` | 2 | 4Gi | No | 2 |
| **client1-cpu** | `enterprise.client1.*` | 2 | 4Gi | No | 2 |
| **client2-gpu** | `enterprise.client2.*` | 4 | 16Gi | **Yes** | 1 |

### Adding New Worker Groups

#### Method 1: Via Database (Recommended)

```sql
-- Connect to database
docker-compose exec postgres psql -U kestra

-- Create new worker group
INSERT INTO worker_groups (name, description, resource_cpu, resource_memory, gpu_enabled)
VALUES ('client3-cpu', 'Dedicated workers for Client 3', '2000m', '4Gi', FALSE);

-- Create namespace mapping
INSERT INTO namespace_worker_groups (namespace_pattern, worker_group_id, priority)
VALUES (
    '^enterprise\.client3\..*',
    (SELECT id FROM worker_groups WHERE name = 'client3-cpu'),
    100
);
```

#### Method 2: Via Docker Compose

1. Add new worker service to `docker-compose.yml`
2. Set environment variables in `.env`
3. Restart: `docker-compose up -d`

---

## 🧪 Testing

### Validate Routing

```bash
# Run comprehensive routing tests
./scripts/test-routing.sh
```

### Manual Testing

1. **Deploy test workflows:**
   - Flows tab → Create → Copy from `test-workflows/`

2. **Execute workflows:**
   - Different namespaces should route to different workers

3. **Check logs:**
   ```bash
   # Shared workers
   docker-compose logs -f worker-shared

   # Client1 workers
   docker-compose logs -f worker-client1

   # Client2 workers
   docker-compose logs -f worker-client2-gpu
   ```

4. **Verify isolation:**
   - Shared workers should NOT execute client1/client2 tasks
   - Client1 workers should ONLY execute enterprise.client1.* tasks
   - Client2 workers should ONLY execute enterprise.client2.* tasks

### Expected Log Output

When a task is routed correctly, you'll see:

```
INFO  i.k.p.WorkerGroupRouter - Routing task from namespace 'enterprise.client1.rag' to worker group 'client1-cpu'
```

---

## 📈 Monitoring

### View Service Status

```bash
# All services
docker-compose ps

# Specific service
docker-compose ps kestra
```

### View Logs

```bash
# All logs
docker-compose logs -f

# Specific service
docker-compose logs -f kestra
docker-compose logs -f worker-shared
docker-compose logs -f postgres

# Last 100 lines
docker-compose logs --tail=100 kestra
```

### Check Worker Group Metrics

```sql
-- Connect to database
docker-compose exec postgres psql -U kestra

-- View worker group configuration
SELECT name, status, resource_cpu, resource_memory, gpu_enabled
FROM worker_groups;

-- View namespace mappings
SELECT wg.name as worker_group, nwg.namespace_pattern, nwg.priority
FROM namespace_worker_groups nwg
JOIN worker_groups wg ON nwg.worker_group_id = wg.id
ORDER BY nwg.priority DESC;
```

---

## 🛠️ Management Commands

### Start Platform

```bash
./scripts/start.sh
```

### Stop Platform

```bash
./scripts/stop.sh
```

### Restart Specific Service

```bash
docker-compose restart kestra
docker-compose restart worker-shared
```

### View Service Health

```bash
# Kestra health endpoint
curl http://localhost:8080/health

# PostgreSQL
docker-compose exec postgres pg_isready -U kestra

# Redis
docker-compose exec redis redis-cli ping
```

### Scale Workers

```bash
# Scale shared workers to 5 replicas
docker-compose up -d --scale worker-shared=5

# Scale client1 workers to 3 replicas
docker-compose up -d --scale worker-client1=3
```

---

## 🔒 Security

### Secrets Management

✅ **NO hardcoded secrets** anywhere in code
✅ **Environment variable isolation** - Each worker group has separate env vars
✅ **`.env` file in `.gitignore`** - Never committed
✅ **`.env.example` as template** - No actual secrets

### Client Isolation

- Client1 workers can ONLY access `CLIENT1_*` environment variables
- Client2 workers can ONLY access `CLIENT2_*` environment variables
- Shared workers have NO client secrets

### Production Recommendations

1. **Use secret management:**
   - AWS Secrets Manager
   - HashiCorp Vault
   - Kubernetes Secrets

2. **Enable authentication:**
   - Add OAuth2/OIDC in Phase 4
   - Restrict network access

3. **Use TLS:**
   - Enable HTTPS for Kestra UI
   - TLS for Kafka, PostgreSQL, Redis

---

## 🐛 Troubleshooting

### Platform Won't Start

```bash
# Check if ports are already in use
lsof -i :8080    # Kestra
lsof -i :5432    # PostgreSQL
lsof -i :9092    # Kafka
lsof -i :6379    # Redis

# Check Docker is running
docker info

# Check .env file exists
ls -la .env

# View detailed logs
docker-compose logs
```

### Workers Not Routing Correctly

```bash
# Check database tables exist
docker-compose exec postgres psql -U kestra -c "\dt worker_groups"

# Check worker group configuration
docker-compose exec postgres psql -U kestra -c "SELECT * FROM worker_groups"

# Check namespace mappings
docker-compose exec postgres psql -U kestra -c "SELECT * FROM namespace_worker_groups"

# Verify WorkerGroupRouter is loaded
docker-compose logs kestra | grep WorkerGroupRouter
```

### Database Connection Errors

```bash
# Verify PostgreSQL is running
docker-compose ps postgres

# Check PostgreSQL logs
docker-compose logs postgres

# Test connection
docker-compose exec postgres psql -U kestra -d kestra -c "SELECT 1"
```

### Kafka Issues

```bash
# Check Kafka is running
docker-compose ps kafka

# List topics (should see workergroup-* topics)
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Check Kafka logs
docker-compose logs kafka
```

---

## 📚 Next Steps

### Phase 2: Workflow Library (Next)

Build 20 production-ready AI workflows:
- RAG pipelines
- ML model training
- LLM batch processing
- Data ETL
- API integrations

### Phase 3: Custom Plugins

Build 25+ connectors:
- LLM providers (OpenAI, Anthropic, Gemini, Cohere)
- Vector databases (Pinecone, Weaviate, Qdrant)
- Data sources (Airtable, Notion, Google Sheets)

### Phase 4: Platform API

Build management API (FastAPI):
- Client management
- Worker group provisioning
- Quota enforcement
- Usage tracking

### Phase 5: Client Portal

Build web UI (Next.js):
- Dashboard
- Workflow management
- Execution logs
- Billing

---

## 🎓 Key Concepts

### Namespace-Based Routing

Workflows are organized into namespaces (e.g., `enterprise.client1.rag`).

The router matches namespaces to worker groups using regex patterns stored in the database.

**Example:**
- Pattern: `^enterprise\.client1\..*`
- Matches: `enterprise.client1.rag`, `enterprise.client1.ml`, `enterprise.client1.etl`
- Routes to: `client1-cpu` worker group

### Worker Group Isolation

Each worker group:
- Consumes from a dedicated Kafka topic
- Has isolated environment variables (secrets)
- Can have different resource limits (CPU, memory, GPU)
- Can be scaled independently

### Priority-Based Matching

When multiple patterns match a namespace, **highest priority wins**.

Default priorities:
- Client-specific patterns: **100**
- Shared patterns: **50**

---

## 📝 Configuration Reference

### Complete .env Template

See `.env.example` for all available configuration options.

### Docker Compose Services

| Service | Description | Port |
|---------|-------------|------|
| `postgres` | PostgreSQL database | 5432 |
| `zookeeper` | Zookeeper (for Kafka) | 2181 |
| `kafka` | Kafka message queue | 9092 |
| `redis` | Redis cache | 6379 |
| `kestra` | Kestra server | 8080 |
| `worker-shared` | Shared worker pool | - |
| `worker-client1` | Client1 dedicated workers | - |
| `worker-client2-gpu` | Client2 GPU workers | - |

---

## ✅ Verification Checklist

After setup, verify:

- [ ] All services running: `docker-compose ps`
- [ ] Kestra UI accessible: http://localhost:8080
- [ ] Database tables exist: `./scripts/test-routing.sh`
- [ ] 3 worker groups configured
- [ ] 5 namespace mappings created
- [ ] Test workflows deployable
- [ ] Logs show correct routing
- [ ] No hardcoded values in code
- [ ] `.env` file excluded from git

---

## 🆘 Support

### Check Logs

99% of issues can be diagnosed from logs:

```bash
docker-compose logs -f
```

### Database Queries

Check configuration in database:

```sql
-- Worker groups
SELECT * FROM worker_groups;

-- Namespace mappings
SELECT * FROM namespace_worker_groups;

-- Recent metrics
SELECT * FROM worker_group_metrics
ORDER BY timestamp DESC LIMIT 10;
```

### Clean Restart

If things are broken, nuclear option:

```bash
# Stop and remove ALL data
docker-compose down -v

# Start fresh
./scripts/start.sh
```

⚠️ **WARNING:** This deletes all workflows, executions, and data!

---

## 📄 License

Built on Kestra (Apache 2.0)
Custom extensions: Your proprietary IP

---

## 🎉 Success!

If you've made it here, you have:

✅ Working multi-worker-group platform
✅ Client isolation via dedicated workers
✅ GPU support for ML workloads
✅ Zero hardcoded configuration
✅ Production-ready deployment

**Phase 1 is COMPLETE!**

Next: Build 20 AI workflows (Phase 2)

---

**Questions?** Check the troubleshooting section or review logs.

**Ready for Phase 2?** Let's build the workflow library!
