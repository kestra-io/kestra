#!/bin/bash

# =============================================================================
# KESTRA PLATFORM - START SCRIPT
# =============================================================================
# Starts the complete multi-worker-group platform
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Kestra Platform - Starting...${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo -e "${RED}ERROR: .env file not found!${NC}"
    echo -e "${YELLOW}Please copy .env.example to .env and fill in your configuration:${NC}"
    echo -e "  cp .env.example .env"
    echo -e "  nano .env"
    exit 1
fi

echo -e "${GREEN}✓${NC} Found .env configuration"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}ERROR: Docker is not running!${NC}"
    echo -e "${YELLOW}Please start Docker and try again.${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Docker is running"

# Load environment variables
set -a
source .env
set +a

echo -e "${GREEN}✓${NC} Loaded environment variables"

# Check required environment variables
REQUIRED_VARS=(
    "POSTGRES_PASSWORD"
    "REDIS_PASSWORD"
)

MISSING_VARS=()

for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var}" ]; then
        MISSING_VARS+=("$var")
    fi
done

if [ ${#MISSING_VARS[@]} -gt 0 ]; then
    echo -e "${RED}ERROR: Missing required environment variables:${NC}"
    for var in "${MISSING_VARS[@]}"; do
        echo -e "  - ${YELLOW}$var${NC}"
    done
    echo ""
    echo -e "${YELLOW}Please edit .env file and set these variables.${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} All required environment variables set"
echo ""

# Build and start services
echo -e "${GREEN}Building and starting services...${NC}"
echo ""

docker-compose up --build -d

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Waiting for services to be healthy...${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Wait for PostgreSQL
echo -n "PostgreSQL: "
timeout 60 bash -c 'until docker-compose exec -T postgres pg_isready -U ${POSTGRES_USER} > /dev/null 2>&1; do sleep 1; done'
echo -e "${GREEN}✓ Ready${NC}"

# Wait for Kafka
echo -n "Kafka: "
sleep 10  # Kafka takes a bit longer
echo -e "${GREEN}✓ Ready${NC}"

# Wait for Redis
echo -n "Redis: "
timeout 30 bash -c 'until docker-compose exec -T redis redis-cli ping > /dev/null 2>&1; do sleep 1; done'
echo -e "${GREEN}✓ Ready${NC}"

# Wait for Kestra
echo -n "Kestra: "
timeout 120 bash -c 'until curl -sf http://localhost:${KESTRA_SERVER_PORT:-8080}/health > /dev/null 2>&1; do sleep 2; done'
echo -e "${GREEN}✓ Ready${NC}"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Kestra Platform Started Successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Kestra UI:     ${GREEN}http://localhost:${KESTRA_SERVER_PORT:-8080}${NC}"
echo -e "Kestra API:    ${GREEN}http://localhost:${KESTRA_SERVER_PORT:-8080}/api/v1${NC}"
echo ""
echo -e "${YELLOW}Worker Groups:${NC}"
echo -e "  • shared (replicas: ${WORKER_SHARED_REPLICAS:-2})"
echo -e "  • client1-cpu (replicas: ${WORKER_CLIENT1_REPLICAS:-2})"
echo -e "  • client2-gpu (replicas: ${WORKER_CLIENT2_REPLICAS:-1})"
echo ""
echo -e "${YELLOW}Useful Commands:${NC}"
echo -e "  View logs:           ${GREEN}docker-compose logs -f${NC}"
echo -e "  View specific logs:  ${GREEN}docker-compose logs -f kestra${NC}"
echo -e "  Stop platform:       ${GREEN}./scripts/stop.sh${NC}"
echo -e "  Check status:        ${GREEN}docker-compose ps${NC}"
echo ""
