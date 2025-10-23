# Kestra on Replit

## Overview
This is the Kestra orchestration platform - an open-source, event-driven workflow orchestration tool. This Replit environment runs the frontend development server for the Kestra UI.

## Current Setup
- **Frontend**: Vue.js 3 + Vite running on port 5000
- **Backend**: Java/Gradle backend (requires Docker to run fully)
- **Language**: Vue.js/TypeScript (frontend), Java 21 (backend)

## What's Running
The frontend development server is configured to run on port 5000 and proxy API requests to a backend server (default: localhost:8080). 

**Note**: The full Kestra backend requires Docker and is not running in this Replit environment. The frontend will show connection errors until a backend is available.

## Project Structure
- `/ui` - Vue.js frontend application
- `/core` - Core Kestra engine
- `/cli` - Command-line interface
- `/webserver` - Backend web server
- `/executor`, `/scheduler`, `/worker` - Backend components

## Recent Changes
- **2025-10-23**: Initial Replit setup
  - Installed Java 21 and Node.js 20
  - Configured Vite to run on port 5000 with host 0.0.0.0
  - Set up frontend workflow for development
  - Configured HMR for Replit's proxy environment

## Development

### Frontend Development
The frontend is already running. Any changes to files in `/ui/src` will automatically hot-reload.

### Running the Full Stack Locally
To run the complete Kestra platform with backend:

1. **Use Docker** (recommended):
   ```bash
   docker run --pull=always --rm -it -p 8080:8080 --user=root \
     -v /var/run/docker.sock:/var/run/docker.sock \
     -v /tmp:/tmp kestra/kestra:latest server local
   ```

2. **Build from source**:
   ```bash
   ./gradlew build -x test
   ./gradlew runLocal
   ```

## Key Files
- `ui/vite.config.js` - Vite configuration (configured for Replit)
- `build.gradle` - Main Gradle build file
- `ui/package.json` - Frontend dependencies

## Resources
- [Kestra Documentation](https://kestra.io/docs)
- [GitHub Repository](https://github.com/kestra-io/kestra)
- [Plugin Ecosystem](https://kestra.io/plugins)
