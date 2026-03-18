# AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is Kestra

Kestra is an open-source event-driven orchestration platform. Users define workflows as YAML "flows" composed of tasks, triggers, and inputs. Built with Java 21 (Micronaut framework) backend and Vue 3 frontend.

## Build & Test Commands

**Prerequisites:** Java 25+ (compiles to Java 21 target), Node.js 22+, Docker. Gradle 9.4 wrapper included.

```bash
# Build
./gradlew build                    # Full build (includes tests)
./gradlew build -x test            # Build without tests
./gradlew shadowJar                # Build fat JAR

# Test
./gradlew test                     # All tests (excludes 'flaky' and 'integration' tags)
./gradlew :core:test               # Single module
./gradlew :core:test --tests "io.kestra.core.runners.FlowableUtilsTest"           # Single class
./gradlew :core:test --tests "io.kestra.core.runners.FlowableUtilsTest.methName"  # Single method

# Run
./gradlew runLocal                 # Local mode (H2 database, port 8080)
make start-standalone-postgres     # Standalone mode with PostgreSQL

# Frontend
cd ui && npm install && npm run dev   # Dev server on port 5173
cd ui && npm run build                # Production build

# Useful Makefile targets
make install                       # Install to ~/.kestra/current
make install-plugins               # Download plugins from API
make health                        # Check if Kestra is running
```

## Project Structure

21 Gradle subprojects organized as:

| Module | Purpose |
|--------|---------|
| `core/` | Core framework: tasks, runners, serialization, topology, events |
| `model/` | Data models and DTOs (flows, executions, triggers, inputs) |
| `cli/` | CLI entry point (`io.kestra.cli.App`), server commands |
| `webserver/` | Micronaut REST API controllers |
| `executor/`, `scheduler/`, `worker/` | Execution engine components |
| `processor/` | Task processing engine |
| `script/` | Script execution engine (Python, Node, Shell, etc.) |
| `jdbc/`, `jdbc-h2/`, `jdbc-mysql/`, `jdbc-postgres/` | Database backends |
| `repository-memory/`, `runner-memory/` | In-memory implementations (for local/test mode) |
| `storage-local/` | Local filesystem storage |
| `ui/` | Vue 3 + TypeScript + Vite frontend |
| `platform/` | BOM for enforced platform dependencies |
| `tests/` | Shared integration test utilities |

## Architecture

**Backend (Java/Micronaut):**
- Entry point: `io.kestra.cli.App` — Picocli-based CLI that boots Micronaut
- Two run modes: **local** (H2 + in-memory queues) and **standalone** (PostgreSQL/MySQL + JDBC queues)
- Flows are YAML-defined, parsed into the model layer, executed by the executor/worker/scheduler triad
- Plugin system: plugins are JARs loaded at startup from `KESTRA_PLUGINS_PATH`; built-in plugins live in `core/src/main/java/io/kestra/plugin/core/`
- Repository pattern: abstract repositories in `core/`, JDBC implementations in `jdbc-*` modules

**Frontend (Vue 3):**
- Vite-based build, source in `ui/src/`
- Communicates with backend REST API

## Testing Patterns

### Backend testing
- **JUnit 5** with Micronaut Test integration
- Custom `@KestraTest` annotation bootstraps the Micronaut context for tests
- Assertions: AssertJ preferred, Hamcrest also used
- Tags: tests tagged `flaky` or `integration` are excluded from default `./gradlew test`
- JDBC modules (`jdbc-h2`, `jdbc-mysql`, `jdbc-postgres`) run tests in parallel
- Test workers get 4GB heap

### Frontend Testing
- Unit tests with Jest
- E2E tests with Playwright
- Component testing with Storybook
- Run `npm run test:unit` and `npm run test:e2e`

### End-to-End Tests

```bash
# Build and start E2E tests
./build-and-start-e2e-tests.sh

# Or use the Makefile
make install
make install-plugins
make start-standalone-postgres
```

## Code Style

- 4 spaces for Java, 2 spaces for YAML/JSON/CSS (see `.editorconfig`)
- UTF-8 encoding, LF line endings, trim trailing whitespace
- Conventional commits: `fix(core):`, `feat(webserver):`, `build(deps):`

## Development Environment

### Quick Setup with Devcontainer

The easiest way to get started is using the provided devcontainer:

1. Install VSCode Remote Development extension
2. Run `Dev Containers: Open Folder in Container...` from command palette
3. Select the Kestra root folder
4. Wait for Gradle build to complete

### Configuration Files

#### Backend Configuration

Create `cli/src/main/resources/application-override.yml`:

**Local Mode (H2 database):**

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

**Standalone Mode (PostgreSQL):**

```yaml
kestra:
  repository:
    type: postgres
  storage:
    type: local
    local:
      base-path: "/app/storage"
  queue:
    type: postgres
  tasks:
    tmp-dir:
      path: /tmp/kestra-wd/tmp
  anonymous-usage-report:
    enabled: false

datasources:
  postgres:
    url: jdbc:postgresql://host.docker.internal:5432/kestra
    driverClassName: org.postgresql.Driver
    username: kestra
    password: k3str4

flyway:
  datasources:
    postgres:
      enabled: true
      locations:
        - classpath:migrations/postgres
      ignore-migration-patterns: "*:missing,*:future"
      out-of-order: true

micronaut:
  server:
    cors:
      enabled: true
      configurations:
        all:
          allowedOrigins:
            - http://localhost:5173
```

## Development Guidelines

### Java Backend
- Use Java 25 features
- Follow Micronaut framework patterns
- Add Swagger annotations for API documentation
- Use annotation processors (enable in IDE)
- Set `MICRONAUT_ENVIRONMENTS=local,override` for custom config
- Set `KESTRA_PLUGINS_PATH` for custom plugin loading

### Vue.js Frontend
- Vue 3 with Composition API
- TypeScript for type safety
- Vite for build tooling
- ESLint and Prettier for code quality
- Component-based architecture in `src/components/`

### Code Style
- Follow `.editorconfig` settings
- Use 4 spaces for Java, 2 spaces for YAML/JSON/CSS
- Enable format on save in VSCode
- Use Prettier for frontend code formatting

## Common Issues and Solutions

### JavaScript Heap Out of Memory

Set `NODE_OPTIONS=--max-old-space-size=4096` environment variable.

### CORS Issues

Ensure backend CORS is configured for `http://localhost:5173` when using frontend dev server.

### Database Connection Issues

- Use `host.docker.internal` instead of `localhost` when connecting from devcontainer
- Verify PostgreSQL is running and accessible
- Check database credentials and permissions

### Gradle Build Issues

- Clear Gradle cache: `./gradlew clean`
- Check Java version compatibility
- Verify all dependencies are available

## Pull Request Guidelines

### Before Submitting

1. Run all tests: `./gradlew test` and `npm test`
2. Check code formatting: `./gradlew spotlessCheck`
3. Verify CORS configuration if changing API
4. Test both local and standalone modes
5. Update documentation for user-facing changes

### Commit Messages

- Follow this commit message format:
```
<type>(<scope>)<!|*>: <description> (<pr id>)

detailed description (optional)

<close|related> #<issue id> (required)

BREAKING CHANGE: <message> (optional)
```
With:
type=chore|feat|fix|refactor|test|docs|build
scope=flows|executions|plugins|namespaces|secrets|assets|storage|triggers|dashboards|apps|tasks|tests|tenants|iam|variables|system|core|deps|version

- Use present tense ("Add feature" not "Added feature")
- Keep commits focused and atomic

### Review Checklist

- [ ] All tests pass
- [ ] Code follows project style guidelines
- [ ] Documentation is updated
- [ ] No breaking changes without migration guide
- [ ] CORS properly configured if API changes
- [ ] Both local and standalone modes tested
