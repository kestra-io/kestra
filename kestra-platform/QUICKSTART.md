# Kestra Platform - Quick Start Guide

**Get running in 5 minutes!**

---

## Prerequisites

- Docker & Docker Compose installed
- 8GB+ RAM
- Ports available: 8080, 5432, 9092, 6379, 2181

---

## Step 1: Configure (30 seconds)

```bash
cd kestra-platform
cp .env.example .env
```

**Edit `.env` and change these TWO values:**

```bash
POSTGRES_PASSWORD=your-secure-password-here    # ← Change this!
REDIS_PASSWORD=your-redis-password-here        # ← Change this!
```

That's it! Everything else has sensible defaults.

---

## Step 2: Start (2 minutes)

```bash
chmod +x scripts/*.sh
./scripts/start.sh
```

Wait for:
```
✓ PostgreSQL: Ready
✓ Kafka: Ready
✓ Redis: Ready
✓ Kestra: Ready

Kestra Platform Started Successfully!
Kestra UI: http://localhost:8080
```

---

## Step 3: Verify (1 minute)

Open browser: **http://localhost:8080**

You should see Kestra UI.

---

## Step 4: Test Routing (1 minute)

```bash
./scripts/test-routing.sh
```

Expected output:
```
✓ Worker group tables exist
✓ Found 3 active worker groups
✓ Found 5 enabled namespace mappings
```

---

## Step 5: Deploy Test Workflow (1 minute)

In Kestra UI:

1. Click **Flows** → **Create**
2. Copy this:

```yaml
id: hello_world
namespace: shared.demo

tasks:
  - id: say_hello
    type: io.kestra.plugin.core.log.Log
    message: "Hello from Kestra Platform!"
```

3. Click **Save**
4. Click **Execute**
5. View logs → Should see "Hello from Kestra Platform!"

---

## ✅ Success!

You now have:
- ✅ Multi-worker-group routing working
- ✅ 3 worker groups (shared, client1, client2)
- ✅ Client isolation configured
- ✅ Ready to build workflows

---

## Next Steps

1. **Deploy more test workflows:**
   - `test-workflows/test-client1-namespace.yml`
   - `test-workflows/test-client2-namespace.yml`

2. **Check worker logs:**
   ```bash
   docker-compose logs -f worker-shared
   docker-compose logs -f worker-client1
   ```

3. **Read full docs:**
   - [README.md](README.md) - Complete documentation
   - [.env.example](.env.example) - All configuration options

---

## Common Commands

```bash
# Stop platform
./scripts/stop.sh

# Restart
./scripts/start.sh

# View all logs
docker-compose logs -f

# Check status
docker-compose ps
```

---

## Troubleshooting

**Platform won't start?**
- Check Docker is running: `docker info`
- Check `.env` file exists: `ls -la .env`
- Check logs: `docker-compose logs`

**Can't access UI?**
- Wait 2-3 minutes for services to start
- Check: `curl http://localhost:8080/health`

**Workers not routing?**
- Run: `./scripts/test-routing.sh`
- Check database: `docker-compose exec postgres psql -U kestra -c "SELECT * FROM worker_groups"`

---

## Need Help?

See [README.md](README.md) for:
- Detailed architecture
- Configuration reference
- Troubleshooting guide
- Advanced features

---

**That's it! You're ready to build! 🚀**
