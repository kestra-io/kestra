# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kestra is an open-source, event-driven orchestration platform built with Java 21 (Micronaut framework) and Vue.js 3. It enables declarative workflow orchestration using YAML definitions with a plugin-based task system.

**Key Technologies:**
- Backend: Java 21, Micronaut, Gradle
- Frontend: Vue 3 (Composition API), Vite, TypeScript
- Databases: PostgreSQL (production), H2 (local dev), MySQL (optional)
- Queue System: Database-backed or Kafka
- Plugin System: Dynamic classloading with annotation-based discovery

## Common Commands

### Backend (Gradle)

```bash
# Build entire project
./gradlew build

# Build without tests (faster)
./gradlew build -x test -x integrationTest -x testCodeCoverageReport --refresh-dependencies

# Run tests
./gradlew test

# Run tests for specific module
./gradlew :core:test
./gradlew :webserver:test

# Run single test class
./gradlew :core:test --tests "io.kestra.core.models.flows.FlowTest"

# Run single test method
./gradlew :core:test --tests "io.kestra.core.models.flows.FlowTest.testFlowSerialization"

# Run local mode (H2 database)
./gradlew runLocal

# Run standalone mode (requires PostgreSQL)
./gradlew runStandalone

# Build executable JAR
./gradlew executableJar

# Create shadow JAR (includes all dependencies)
./gradlew shadowJar

# Create JLink runtime distribution
./gradlew jlinkZip

# Clean build
./gradlew clean

# Run end-to-end tests
./gradlew e2eTestsCheck -Pe2e-tests

# Build frontend as part of Gradle
./gradlew :ui:assembleFrontend

# Run with debug JVM
./gradlew runLocal --debug-jvm
```

### Frontend (npm/vite)

```bash
cd ui

# Install dependencies
npm install

# Run development server (port 5173)
npm run dev

# Build for production
npm run build

# Run unit tests
npm run test:unit

# Run all tests with coverage
npm run test:all

# Run E2E tests
npm run test:e2e

# Lint and fix
npm run lint

# Type checking
npm run check:types

# Run Storybook
npm run storybook
```

### Using Makefile (Development)

```bash
# Build and install Kestra locally to ~/.kestra/current
make install

# Install plugins
make install-plugins

# Start standalone mode with PostgreSQL
make start-standalone-postgres

# Start local mode (in-memory)
make start-standalone-local

# Kill running Kestra process
make kill

# Check health
make health

# Build Docker image
make build-docker
```

### Docker Compose

```bash
# Start PostgreSQL and Kestra
docker compose up -d

# Stop services
docker compose down

# View logs
docker compose logs -f kestra
```

## High-Level Architecture

### Module Dependencies

The codebase follows a layered architecture with clear dependency flow:

```
┌─────────────────────────────────────────────────────────────┐
│                      UI (Vue.js)                            │
│                 (Communicates via REST API)                 │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                  WEBSERVER (REST API)                       │
│           (Micronaut Controllers & Routes)                  │
└────────────────────────┬────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
    ┌───▼────┐      ┌───▼────┐      ┌───▼────┐
    │EXECUTOR│      │SCHEDULER│    │WORKER │
    └───┬────┘      └───┬────┘      └───┬────┘
        │                │                │
        └────────────────┼────────────────┘
                         │
        ┌────────────────▼────────────────┐
        │         CORE (Foundation)       │
        │  - Models (Flow, Execution)     │
        │  - RunContext                   │
        │  - Repository Interfaces        │
        │  - Queue Interfaces             │
        │  - Plugin System                │
        └─────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
    ┌───▼────────┐   ┌───▼─────┐    ┌──▼──────┐
    │JDBC-based  │   │Storage   │    │Script   │
    │Repository  │   │Backends  │    │Engine   │
    │(PG, H2, My)│   │(Local FS)│    │(Py, JS) │
    └────────────┘   └──────────┘    └─────────┘
```

**Dependency Summary:**
- **`core/`**: Foundation layer - contains all abstractions, domain models, and interfaces. No dependencies on other internal modules.
- **`executor/`, `scheduler/`, `worker/`**: Orchestration layer - depend on `core/` only. Handle execution flow.
- **`webserver/`**: API layer - depends on orchestration modules and `core/`. Exposes REST endpoints.
- **`ui/`**: Independent client - communicates with backend via REST API, no direct Java dependencies.
- **`jdbc-*/`, `storage-local/`, `script/`**: Implementation layer - implement interfaces from `core/`.

### Core Execution Flow

Kestra uses a **queue-based event-driven architecture** with clear component separation:

```
SCHEDULER → executionQueue → EXECUTOR → workerJobQueue → WORKER
                                  ↑                          ↓
                                  └──────── resultQueue ─────┘
```

1. **Scheduler** (`scheduler/`): Monitors flows for triggers (cron schedules, events) and creates `Execution` records when triggered
2. **Executor** (`executor/`): Orchestrates execution by resolving the DAG, managing state transitions, and queuing tasks to workers
3. **Worker** (`worker/`): Executes individual tasks by loading plugins and running `task.run(runContext)`

### Key Domain Models

**Flow** (`core/models/flows/Flow.java`):
- Versioned workflow definition containing tasks, triggers, inputs, and variables
- Stored via `FlowRepositoryInterface`
- Supports namespaces for organization

**Execution** (`core/models/executions/Execution.java`):
- Represents one flow run instance
- Contains state machine tracking (`CREATED → RUNNING → SUCCESS/FAILED`)
- Includes list of `TaskRun` objects for each task execution
- Stores variables and outputs

**TaskRun** (`core/models/executions/TaskRun.java`):
- Records execution of a single task within a flow
- Contains attempts (for retry tracking)
- Stores task outputs and state

**State** (`core/models/flows/State.java`):
- State machine: `CREATED`, `RUNNING`, `SUCCESS`, `FAILED`, `WARNING`, `KILLED`, `PAUSED`
- Maintains history for audit trail

### Module Breakdown

**core/**: Core framework containing:
- `models/`: Domain objects (Flow, Execution, TaskRun, State)
- `runners/`: `RunContext` (execution context), `Executor` interfaces
- `repositories/`: Abstract interfaces for persistence (Flow, Execution, Log, Trigger repositories)
- `queues/`: `QueueInterface` for async messaging
- `plugins/`: `PluginRegistry`, `PluginScanner`, task abstractions
- `tasks/`: Built-in task implementations
- `exceptions/`: Custom exception hierarchy

**executor/**: Execution orchestration
- `ExecutorService`: Main orchestration logic
- DAG resolution using `FlowableUtils`
- State machine management
- Task result aggregation

**scheduler/**: Trigger monitoring
- `AbstractScheduler`: Base scheduler implementation
- Evaluates polling triggers (cron, schedule)
- Handles realtime triggers (webhooks, events)
- Creates executions and emits to queue

**worker/**: Task execution
- `DefaultWorker`: Task execution engine
- Plugin loading and task instantiation
- `RunContext` creation with execution variables
- Result emission back to executor

**webserver/**: REST API (Micronaut)
- `controllers/api/FlowController.java`: Flow CRUD operations
- `controllers/api/ExecutionController.java`: Execution management and triggering
- `controllers/api/LogController.java`: Task log streaming
- `controllers/api/PluginController.java`: Plugin discovery

**ui/**: Vue.js frontend
- `src/components/`: Reusable Vue components
- `src/stores/`: Pinia state management stores
- `src/routes/`: Vue Router configuration
- `src/services/`: API client services (axios)
- `src/utils/`: Helper utilities

**jdbc/**, **jdbc-h2/**, **jdbc-postgres/**, **jdbc-mysql/**: Database implementations
- Repository implementations using JDBC
- Flyway migrations in `resources/migrations/`

**cli/**: Command-line interface
- `App.java`: Main entry point (`io.kestra.cli.App`)
- Server modes: `local` (H2 + in-memory), `standalone` (all-in-one with PostgreSQL)
- Plugin management commands

**model/**: Shared data models and DTOs

**script/**: Script execution engine for Python, Node.js, R, Shell, etc.

**storage-local/**: Local filesystem storage implementation

**repository-memory/**, **runner-memory/**: In-memory implementations (testing/local dev)

**tests/**: Integration test framework

**e2e-tests/**: End-to-end test suite

## Plugin System

### Plugin Types

1. **RunnableTask**: Executed in Worker (e.g., HTTP request, database query, script execution)
2. **FlowableTask**: Orchestrated by Executor (e.g., Parallel, ForEachItem, Subflow)
3. **Trigger**: Evaluated by Scheduler (Schedule, Webhook, File detection)

### Plugin Lifecycle

1. Flow YAML specifies task type: `type: io.kestra.plugin.core.http.Request`
2. Jackson deserializes YAML to Java `Task` object
3. Worker uses `PluginRegistry.findClassByIdentifier()` to resolve class
4. Task is instantiated and `run(runContext)` is called
5. Outputs are captured and passed to next tasks

### Plugin Loading

- Plugins are JAR files placed in `KESTRA_PLUGINS_PATH` directory
- `PluginScanner` discovers classes with `@Plugin` annotation
- `PluginClassLoader` provides classpath isolation
- Plugin metadata exposed via REST API at `/api/v1/plugins`

## RunContext: Execution Context

`RunContext` is the primary interface tasks use to interact with Kestra:

```java
// Template rendering with Pebble syntax
String rendered = runContext.render("{{ inputs.myValue }}");

// Access variables from flow, execution, previous tasks
Map<String, Object> variables = runContext.variables();

// File storage operations
URI uri = runContext.storage().putFile(file);
InputStream stream = runContext.storage().getFile(uri);

// Logging
runContext.logger().info("Task started");

// Key-value store for inter-task communication
runContext.getKVStore().put("key", "value");

// Encryption
String encrypted = runContext.encrypt("secret");
String decrypted = runContext.decrypt(encrypted);
```

## Repository Pattern

All data access goes through repository interfaces allowing backend swapping:

- `FlowRepositoryInterface`: Flow CRUD with versioning
- `ExecutionRepositoryInterface`: Execution tracking and querying
- `LogRepositoryInterface`: Task log storage
- `TriggerRepositoryInterface`: Trigger state persistence

Implementations:
- JDBC-based (PostgreSQL, MySQL, H2) in `jdbc-*/`
- In-memory (testing) in `repository-memory/`

### JOOQ Query Builder

The JDBC repository implementations use JOOQ for type-safe SQL queries. JOOQ provides compile-time SQL validation and prevents SQL injection.

**JOOQ Basics:**

```java
// In a JDBC repository implementation
import static org.jooq.impl.DSL.*;
import static io.kestra.jdbc.AbstractJdbcRepository.*;

// Select query
DSL.selectFrom(FLOWS)
    .where(FLOWS.TENANT_ID.eq(tenantId))
    .and(FLOWS.NAMESPACE.eq(namespace))
    .fetch();

// Insert query
DSL.insertInto(EXECUTIONS)
    .set(EXECUTIONS.TENANT_ID, tenantId)
    .set(EXECUTIONS.FLOW_ID, flowId)
    .set(EXECUTIONS.STATE, "RUNNING")
    .execute();

// Update query
DSL.update(EXECUTIONS)
    .set(EXECUTIONS.STATE, newState)
    .where(EXECUTIONS.ID.eq(executionId))
    .and(EXECUTIONS.TENANT_ID.eq(tenantId))
    .execute();

// Delete query
DSL.deleteFrom(TASK_RUNS)
    .where(TASK_RUNS.EXECUTION_ID.eq(executionId))
    .and(TASK_RUNS.TENANT_ID.eq(tenantId))
    .execute();
```

**Generated Tables:**

Table references are auto-generated from the database schema during build:
- Located in `jdbc-*/build/generated-sources/`
- Examples: `Tables.FLOWS`, `Tables.EXECUTIONS`, `Tables.TASK_RUNS`
- Column references: `FLOWS.ID`, `FLOWS.NAMESPACE`, `FLOWS.TENANT_ID`

**Migration Usage:**

Schema changes use Flyway migrations:
1. Create migration file: `jdbc-postgres/src/main/resources/migrations/V{number}__{description}.sql`
2. Use standard SQL (database-specific if needed)
3. Applied automatically on startup
4. JOOQ regenerates classes from updated schema on build

**Querying Patterns:**

```java
// Fetch single record
Flow flow = DSL.selectFrom(FLOWS)
    .where(FLOWS.ID.eq(flowId))
    .and(FLOWS.TENANT_ID.eq(tenantId))
    .fetchOne()
    .map(record -> mapper.fromRecord(record));

// Fetch multiple records
List<Flow> flows = DSL.selectFrom(FLOWS)
    .where(FLOWS.NAMESPACE.eq(namespace))
    .and(FLOWS.TENANT_ID.eq(tenantId))
    .orderBy(FLOWS.CREATED_AT.desc())
    .fetch()
    .map(record -> mapper.fromRecord(record));

// Count records
int count = DSL.selectCount()
    .from(FLOWS)
    .where(FLOWS.TENANT_ID.eq(tenantId))
    .fetchOne(count());

// Join example
List<ExecutionWithFlow> results = DSL.select()
    .from(EXECUTIONS)
    .leftJoin(FLOWS).on(EXECUTIONS.FLOW_ID.eq(FLOWS.ID))
    .where(EXECUTIONS.TENANT_ID.eq(tenantId))
    .fetch()
    .map(record -> mapper.fromRecord(record));
```

**Best Practices:**

1. Always include tenant ID filter in WHERE clauses
2. Use parameterized queries (avoid string concatenation)
3. Map JOOQ records to domain objects using mappers
4. Handle NULL values explicitly
5. Use transactions for multi-step operations
6. Test queries with multiple tenants

## Queue System

Async communication via `QueueInterface<T>`:

- `executionQueue`: Scheduler → Executor
- `workerJobQueue`: Executor → Worker
- `workerTaskResultQueue`: Worker → Executor
- `logQueue`: Task logs
- `killQueue`: Execution cancellation signals
- `triggerQueue`: Trigger evaluation results

## Configuration

### Backend Configuration

Configuration files in `cli/src/main/resources/`:
- `application.yml`: Base configuration
- `application-override.yml`: Local overrides (gitignored)

Key configuration sections:
```yaml
kestra:
  repository:
    type: postgres  # or h2, mysql
  storage:
    type: local
  queue:
    type: postgres  # or kafka

datasources:
  postgres:
    url: jdbc:postgresql://localhost:5432/kestra
    username: kestra
    password: k3str4

micronaut:
  server:
    port: 8080
    cors:
      enabled: true
```

### Environment Variables

- `MICRONAUT_ENVIRONMENTS`: Config profiles (e.g., `local,override`)
- `KESTRA_PLUGINS_PATH`: Custom plugin directory
- `KESTRA_CONFIGURATION`: Inline YAML configuration (overrides files)
- `NODE_OPTIONS`: Node.js memory settings (`--max-old-space-size=4096`)

### Configuration Profiles

Kestra uses Micronaut's environment-based configuration system:

**Configuration Files** (in `cli/src/main/resources/`):
- `application.yml`: Base configuration, committed to repo
- `application-local.yml`: Local development overrides (apply when `MICRONAUT_ENVIRONMENTS=local`)
- `application-override.yml`: Local machine overrides (gitignored, for personal settings)
- `application-{mode}.yml`: Mode-specific configs (e.g., `application-standalone.yml`)

**Configuration Activation:**

Set `MICRONAUT_ENVIRONMENTS` to activate profiles:
```bash
# Activate local profile
export MICRONAUT_ENVIRONMENTS=local
./gradlew runLocal

# Activate multiple profiles (comma-separated)
export MICRONAUT_ENVIRONMENTS=local,override
./gradlew runLocal
```

**Configuration Precedence** (highest to lowest):
1. `KESTRA_CONFIGURATION` environment variable (inline YAML)
2. `-Dkey=value` JVM system properties
3. `application-override.yml` (local machine)
4. Environment-specific files (`application-{env}.yml`)
5. `application.yml` (base)

**Common Local Override Example** (`application-override.yml`):
```yaml
micronaut:
  server:
    cors:
      enabled: true
      configurations:
        all:
          allowedOrigins:
            - http://localhost:5173

kestra:
  repository:
    type: h2  # Use H2 for local dev
  storage:
    type: local
  queue:
    type: memory  # In-memory queue for fast iteration

logging:
  level:
    io.kestra.core: DEBUG
    io.kestra.executor: TRACE
```

## Testing

### Backend Testing

Tests use **JUnit 5** with **Micronaut Test** framework:

```java
@MicronautTest
class MyServiceTest {
    @Inject
    MyService service;

    @Test
    void testSomething() {
        // test implementation
    }
}
```

- Tests in `src/test/java/` mirror `src/main/java/` structure
- Use `@Property` to override config for tests
- H2 database used by default for tests
- Parallel test execution enabled for core modules

### Frontend Testing

- **Vitest** for unit tests
- **Playwright** for E2E tests
- **Storybook** for component testing

Run tests:
```bash
cd ui
npm run test:unit     # Unit tests
npm run test:e2e      # E2E tests
npm run test:all      # All tests with coverage
```

## Development Workflow

### Development Containers (DevContainer)

**VSCode Remote Development** (Recommended for fast setup):

The repository includes a devcontainer configuration (`.devcontainer/`) that provides a pre-configured development environment:

**Features:**
- Java 21, Node.js 22+, Python 3 pre-installed
- PostgreSQL database
- All required VSCode extensions
- Port forwarding: 5173 (frontend), 8080 (API)
- Git and Docker pre-configured

**Setup:**
1. Install VSCode Remote Development extension
2. Open the repository folder in VSCode
3. Click "Reopen in Container" prompt or run `Dev Containers: Open Folder in Container`
4. Wait for container to build and dependencies to install (~5-10 minutes first time)
5. Run: `./gradlew runLocal` in terminal
6. In another terminal: `cd ui && npm run dev`
7. Access UI at `http://localhost:5173`, API at `http://localhost:8080`

**Advantages:**
- Consistent environment across team
- No local installation conflicts
- Isolated from host system
- Easy cleanup (just delete container)

### Running Locally

**Option 1: Local mode (H2 database, fastest)**
```bash
./gradlew runLocal
cd ui && npm run dev
# Access UI at http://localhost:5173
# API at http://localhost:8080
```

**Option 2: Standalone mode (PostgreSQL, production-like)**
```bash
docker compose up postgres -d
./gradlew runStandalone
cd ui && npm run dev
```

**Option 3: Full Docker**
```bash
docker compose up -d
# Access at http://localhost:8080
```

### CORS Configuration

When running frontend dev server (port 5173) with backend (port 8080), add to `application-override.yml`:
```yaml
micronaut:
  server:
    cors:
      enabled: true
      configurations:
        all:
          allowedOrigins:
            - http://localhost:5173
```

### Making Changes (Local Development)

1. **Backend changes**: Edit Java files, Gradle will auto-recompile (if using IDE with auto-build)
2. **Frontend changes**: Vite hot-reload automatically updates browser
3. **Database schema**: Add Flyway migrations to `jdbc-*/src/main/resources/migrations/`

### Running Single Tests

```bash
# Run specific test class
./gradlew :core:test --tests "io.kestra.core.models.flows.FlowTest"

# Run specific test method
./gradlew :core:test --tests "io.kestra.core.models.flows.FlowTest.testFlowSerialization"

# Run with debug
./gradlew :core:test --tests "MyTest" --debug-jvm
```

## Key Files to Start With

**Understanding the architecture:**
- `core/src/main/java/io/kestra/core/models/flows/Flow.java`
- `core/src/main/java/io/kestra/core/models/executions/Execution.java`
- `core/src/main/java/io/kestra/core/runners/RunContext.java`
- `executor/src/main/java/io/kestra/executor/ExecutorService.java`
- `scheduler/src/main/java/io/kestra/scheduler/AbstractScheduler.java`
- `worker/src/main/java/io/kestra/worker/DefaultWorker.java`

**Understanding REST API:**
- `webserver/src/main/java/io/kestra/webserver/controllers/api/FlowController.java`
- `webserver/src/main/java/io/kestra/webserver/controllers/api/ExecutionController.java`

**Understanding plugins:**
- `core/src/main/java/io/kestra/core/models/tasks/RunnableTask.java`
- `core/src/main/java/io/kestra/core/models/tasks/FlowableTask.java`
- `core/src/main/java/io/kestra/core/plugins/PluginRegistry.java`

**Understanding frontend:**
- `ui/src/main.js`: Vue app entry point
- `ui/src/routes/`: Application routing
- `ui/src/stores/`: State management
- `ui/src/components/`: Reusable components

## Important Patterns

### State Transitions

Executions follow a state machine with careful transition validation. Always update states through proper methods, not direct field assignment.

### Multi-tenancy

The system supports multi-tenancy via `TenantInterface`. All repositories automatically filter by `tenantId`. When adding new queries, ensure tenant isolation.

**Key Multi-Tenancy Patterns:**

1. **Automatic Tenant Filtering**: Repository implementations automatically filter results by tenant ID
   ```java
   // Repository interface method
   List<Flow> findByNamespace(String namespace);

   // Implementation automatically adds: WHERE tenant_id = ? AND namespace = ?
   ```

2. **Tenant Context**: Tenant ID is typically obtained from:
   - HTTP request headers (in REST API)
   - Execution context (in task execution)
   - Configuration (for CLI operations)

3. **Adding Tenant-Aware Queries**:
   - When creating new repository methods, ensure all queries include tenant filter
   - Use JOOQ's type-safe query builder to avoid SQL injection
   - Example: `DSL.selectFrom(FLOWS).where(FLOWS.TENANT_ID.eq(tenantId).and(...))`

4. **Cross-Tenant Data Leakage Prevention**:
   - Never assume tenant from request context alone
   - Always pass tenant ID explicitly to repository methods
   - Test queries with multiple tenants to ensure isolation

### Variable Resolution

Tasks use Pebble templating for variable resolution:
```yaml
tasks:
  - id: mytask
    type: io.kestra.plugin.core.log.Log
    message: "{{ inputs.name }}"  # Resolved at runtime
```

Variables available:
- `{{ inputs.* }}`: Flow inputs
- `{{ outputs.taskId.* }}`: Previous task outputs
- `{{ execution.* }}`: Execution metadata
- `{{ flow.* }}`: Flow definition
- `{{ vars.* }}`: Flow-level variables

### Error Handling

Use `errors` property on tasks to define error handlers:
```yaml
tasks:
  - id: task1
    type: SomeTask
errors:
  - id: error_handler
    type: io.kestra.plugin.core.log.Log
    message: "Task failed: {{ task.id }}"
```

### Task Output Format

Tasks output data that becomes available to downstream tasks through the `outputs` context.

**Output Structure:**
```java
// In a RunnableTask
public Output run(RunContext runContext) throws Exception {
    // Process logic...

    return Output.builder()
        .exitCode(0)
        .put("result", "processed_value")
        .put("status", "success")
        .put("metrics", Map.of(
            "duration", 1234,
            "itemsProcessed", 42
        ))
        .build();
}
```

**Accessing Outputs in Downstream Tasks:**

Outputs are accessed via Pebble templating in downstream task configurations:
```yaml
tasks:
  - id: step1
    type: io.kestra.plugin.core.http.Request
    url: "https://api.example.com/data"

  - id: step2
    type: io.kestra.plugin.core.log.Log
    message: "Previous task output: {{ outputs.step1.body }}"
```

**Output Variable Scope:**
- `{{ outputs.taskId.property }}`: Access specific task output by ID
- `{{ outputs.taskId }}`: Get entire task output object
- Available after task completes successfully
- Not available if task failed (unless using error handlers)
- Available in both downstream tasks and task conditions

**Output Types Commonly Used:**
- Simple properties: `"status": "success"`
- Complex objects: `"data": { "nested": "value" }`
- Arrays: `"items": [1, 2, 3]`
- File URIs: `"file": "kestra://path/to/file"`

**Example - HTTP Task Output:**
```yaml
tasks:
  - id: fetch_data
    type: io.kestra.plugin.core.http.Request
    url: "https://jsonplaceholder.typicode.com/users/1"

  - id: process_response
    type: io.kestra.plugin.core.log.Log
    message: |
      User: {{ outputs.fetch_data.body.name }}
      Email: {{ outputs.fetch_data.body.email }}
```

## Code Style

- **Java**: 4 spaces indentation, follow Google Java Style Guide
- **JavaScript/Vue**: 2 spaces indentation, Prettier for formatting
- **YAML**: 2 spaces indentation
- Enable "format on save" in your IDE
- Run `npm run lint` before committing frontend changes

## Build Configuration

- Gradle wrapper: `./gradlew` (Unix) or `gradlew.bat` (Windows)
- Java target: Java 21
- Main class: `io.kestra.cli.App`
- Shadow JAR includes all dependencies for distribution
- Frontend builds are integrated into Gradle via `ui:assembleFrontend` task

## Performance Considerations

- Tests run with 4GB heap: `-Xmx4g`
- Gradle uses 2GB heap: `-Xmx2g` (in `gradle.properties`)
- Parallel test execution enabled for core modules
- Build caching enabled by default
- Use `buildSkipTests` target for faster iteration during development

## Security Notes

- Never commit credentials to version control
- Use environment variables for secrets in flows: `{{ secret('MY_SECRET') }}`
- Secret environment variables must be base64-encoded and prefixed with `SECRET_`
- Example: `SECRET_MY_API_KEY=<base64-encoded-value>`
- Encryption key configured via `kestra.encryption.secret-key`

## Debugging

### Backend Debugging

Run with debug enabled:
```bash
./gradlew runLocal --debug-jvm
```

Then attach IDE debugger to port 5005.

For IntelliJ IDEA:
1. Create Run Configuration with main class `io.kestra.cli.App`
2. Add program arguments: `server local --plugins local/plugins`
3. Add environment variable: `MICRONAUT_ENVIRONMENTS=local,override`
4. Run in Debug mode

### Frontend Debugging

Use Vue DevTools browser extension for component inspection and state debugging.

### Logging

Configure logging in `logback.xml`:
```xml
<logger name="io.kestra.core" level="DEBUG"/>
<logger name="io.kestra.executor" level="TRACE"/>
```

## Resources

- Main docs: https://kestra.io/docs
- Plugin Developer Guide: https://kestra.io/docs/plugin-developer-guide/
- API docs: http://localhost:8080/swagger-ui (when running)
- Community Slack: https://kestra.io/slack
- GitHub Issues: https://github.com/kestra-io/kestra/issues