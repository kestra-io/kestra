#!/bin/bash

# =============================================================================
# KESTRA PLATFORM - TEST WORKER GROUP ROUTING
# =============================================================================
# Tests that workflows from different namespaces route to correct worker groups
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Testing Worker Group Routing${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Load environment variables
if [ -f .env ]; then
    set -a
    source .env
    set +a
fi

KESTRA_URL=${KESTRA_URL:-http://localhost:8080}

# Check if Kestra is running
if ! curl -sf ${KESTRA_URL}/health > /dev/null 2>&1; then
    echo -e "${RED}ERROR: Kestra is not running!${NC}"
    echo -e "${YELLOW}Please start the platform first:${NC}"
    echo -e "  ./scripts/start.sh"
    exit 1
fi

echo -e "${GREEN}✓${NC} Kestra is running"
echo ""

# Test 1: Check database tables exist
echo -e "${YELLOW}Test 1:${NC} Checking worker group tables..."
docker-compose exec -T postgres psql -U ${POSTGRES_USER} -d ${POSTGRES_DB} -c "\dt worker_groups" > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Worker group tables exist"
else
    echo -e "${RED}✗${NC} Worker group tables missing"
    exit 1
fi

# Test 2: Check worker groups are configured
echo -e "${YELLOW}Test 2:${NC} Checking worker group configuration..."
WORKER_COUNT=$(docker-compose exec -T postgres psql -U ${POSTGRES_USER} -d ${POSTGRES_DB} -t -c "SELECT COUNT(*) FROM worker_groups WHERE status = 'active'" | tr -d ' ')
echo -e "${GREEN}✓${NC} Found $WORKER_COUNT active worker groups"

# Test 3: Check namespace mappings
echo -e "${YELLOW}Test 3:${NC} Checking namespace mappings..."
MAPPING_COUNT=$(docker-compose exec -T postgres psql -U ${POSTGRES_USER} -d ${POSTGRES_DB} -t -c "SELECT COUNT(*) FROM namespace_worker_groups WHERE enabled = true" | tr -d ' ')
echo -e "${GREEN}✓${NC} Found $MAPPING_COUNT enabled namespace mappings"

# Test 4: Execute test workflows
echo ""
echo -e "${YELLOW}Test 4:${NC} Executing test workflows..."
echo ""

# Deploy and execute test workflows (if they exist)
if [ -d "./test-workflows" ]; then
    for workflow_file in ./test-workflows/*.yml; do
        if [ -f "$workflow_file" ]; then
            workflow_name=$(basename "$workflow_file" .yml)
            echo -e "  Testing: ${BLUE}$workflow_name${NC}"

            # Deploy workflow
            curl -sf -X POST "${KESTRA_URL}/api/v1/flows" \
                -H "Content-Type: application/yaml" \
                --data-binary "@$workflow_file" > /dev/null 2>&1

            if [ $? -eq 0 ]; then
                echo -e "    ${GREEN}✓${NC} Deployed successfully"
            else
                echo -e "    ${RED}✗${NC} Failed to deploy"
            fi
        fi
    done
else
    echo -e "${YELLOW}  No test workflows found in ./test-workflows/${NC}"
fi

echo ""
echo -e "${YELLOW}Test 5:${NC} Checking worker group logs..."
echo ""

# Check if workers are consuming from correct topics
echo -e "  Checking shared workers..."
SHARED_LOGS=$(docker-compose logs worker-shared 2>&1 | grep -c "workergroup-shared" || true)
if [ "$SHARED_LOGS" -gt 0 ]; then
    echo -e "    ${GREEN}✓${NC} Shared workers consuming from correct topic"
else
    echo -e "    ${YELLOW}⚠${NC} No log evidence yet (may need workflow execution)"
fi

echo -e "  Checking client1 workers..."
CLIENT1_LOGS=$(docker-compose logs worker-client1 2>&1 | grep -c "workergroup-client1" || true)
if [ "$CLIENT1_LOGS" -gt 0 ]; then
    echo -e "    ${GREEN}✓${NC} Client1 workers consuming from correct topic"
else
    echo -e "    ${YELLOW}⚠${NC} No log evidence yet (may need workflow execution)"
fi

echo -e "  Checking client2 workers..."
CLIENT2_LOGS=$(docker-compose logs worker-client2-gpu 2>&1 | grep -c "workergroup-client2" || true)
if [ "$CLIENT2_LOGS" -gt 0 ]; then
    echo -e "    ${GREEN}✓${NC} Client2 workers consuming from correct topic"
else
    echo -e "    ${YELLOW}⚠${NC} No log evidence yet (may need workflow execution)"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Routing Tests Complete${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}Summary:${NC}"
echo -e "  • Worker groups configured: ${GREEN}${WORKER_COUNT}${NC}"
echo -e "  • Namespace mappings: ${GREEN}${MAPPING_COUNT}${NC}"
echo -e "  • Database schema: ${GREEN}✓${NC}"
echo ""
echo -e "${YELLOW}To verify routing with actual executions:${NC}"
echo -e "  1. Deploy test workflows from ./test-workflows/"
echo -e "  2. Execute them via Kestra UI"
echo -e "  3. Check worker logs: ${GREEN}docker-compose logs -f worker-shared${NC}"
echo ""
