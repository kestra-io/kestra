#!/bin/bash

# =============================================================================
# KESTRA PLATFORM - STOP SCRIPT
# =============================================================================
# Stops all services
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}Kestra Platform - Stopping...${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# Stop all services
docker-compose down

echo ""
echo -e "${GREEN}✓ All services stopped${NC}"
echo ""
echo -e "${YELLOW}Note:${NC} Data volumes are preserved. To remove volumes, run:"
echo -e "  ${RED}docker-compose down -v${NC}"
echo ""
