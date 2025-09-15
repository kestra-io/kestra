# Kestra AGENTS.md

This file provides guidance for AI coding agents working on the Kestra project. Kestra is an open-source data orchestration and scheduling platform built with Java (Micronaut) and Vue.js.

## Repository Layout

- **`core/`**: Core Kestra framework and task definitions
- **`cli/`**: Command-line interface and server implementation
- **`webserver/`**: REST API server implementation
- **`ui/`**: Vue.js frontend application
- **`jdbc-*`**: Database connector modules (H2, MySQL, PostgreSQL)
- **`script/`**: Script execution engine
- **`storage-local/`**: Local file storage implementation
- **`repository-memory/`**: In-memory repository implementation
- **`runner-memory/`**: In-memory execution runner
- **`processor/`**: Task processing engine
- **`model/`**: Data models and Data Transfer Objects
- **`platform/`**: Platform-specific implementations
- **`tests/`**: Integration test framework
- **`e2e-tests/`**: End-to-end testing suite

## Development Environment

### Prerequisites

- Java 21+
- Node.js 22+ and npm
- Python 3, pip, and python venv
- Docker & Docker Compose
- Gradle (wrapper included)

### Quick Setup with Devcontainer

The easiest way to get started is using the provided devcontainer:

1. Install VSCode Remote Development extension
2. Run `Dev Containers: Open Folder in Container...` from command palette
3. Select the Kestra root folder
4. Wait for Gradle build to complete

### Manual Setup

1. Clone the repository
2. Run `./gradlew build` to build the backend
3. Navigate to `ui/` and run `npm install`
4. Create configuration files as described below

## Configuration Files

### Backend Configuration

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

### Frontend Configuration

Create `ui/.env.development.local` for environment variables.

## Running the Application

### Backend

- **Local mode**: `./gradlew runLocal` (uses H2 database)
- **Standalone mode**: Use VSCode Run and Debug with main class `io.kestra.cli.App` and args `server standalone`

### Frontend

- Navigate to `ui/` directory
- Run `npm run dev` for development server (port 5173)
- Run `npm run build` for production build

## Building and Testing

### Backend

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run specific module tests
./gradlew :core:test

# Clean build
./gradlew clean build
```

### Frontend

```bash
cd ui
npm install
npm run test
npm run lint
npm run build
```

### End-to-End Tests

```bash
# Build and start E2E tests
./build-and-start-e2e-tests.sh

# Or use the Makefile
make install
make install-plugins
make start-standalone-postgres
```

## Development Guidelines

### Java Backend

- Use Java 21 features
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

## Testing Strategy

### Backend Testing

- Unit tests in `src/test/java/`
- Integration tests in `tests/` module
- Use Micronaut test framework
- Test both local and standalone modes

### Frontend Testing
- Unit tests with Jest
- E2E tests with Playwright
- Component testing with Storybook
- Run `npm run test:unit` and `npm run test:e2e`

## Plugin Development

### Creating Plugins

- Follow the [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/)
- Place JAR files in `KESTRA_PLUGINS_PATH`
- Use the plugin template structure
- Test with both local and standalone modes

### Plugin Loading

- Set `KESTRA_PLUGINS_PATH` environment variable
- Use devcontainer mounts for local development
- Plugins are loaded at startup

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

- Follow conventional commit format
- Use present tense ("Add feature" not "Added feature")
- Reference issue numbers when applicable
- Keep commits focused and atomic

### Review Checklist

- [ ] All tests pass
- [ ] Code follows project style guidelines
- [ ] Documentation is updated
- [ ] No breaking changes without migration guide
- [ ] CORS properly configured if API changes
- [ ] Both local and standalone modes tested

## Useful Commands

```bash
# Quick development commands
./gradlew runLocal                    # Start local backend
./gradlew :ui:build                   # Build frontend
./gradlew clean build                 # Clean rebuild
npm run dev                           # Start frontend dev server
make install                          # Install Kestra locally
make start-standalone-postgres        # Start with PostgreSQL

# Testing commands
./gradlew test                        # Run all backend tests
./gradlew :core:test                  # Run specific module tests
npm run test                          # Run frontend tests
npm run lint                          # Lint frontend code
```

## Getting Help

- Open a [GitHub issue](https://github.com/kestra-io/kestra/issues)
- Join the [Kestra Slack community](https://kestra.io/slack)
- Check the [main documentation](https://kestra.io/docs)

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MICRONAUT_ENVIRONMENTS` | Custom config environments | `local,override` |
| `KESTRA_PLUGINS_PATH` | Path to custom plugins | `/workspaces/kestra/local/plugins` |
| `NODE_OPTIONS` | Node.js options | `--max-old-space-size=4096` |
| `JAVA_HOME` | Java installation path | `/usr/java/jdk-21` |

Remember: Always test your changes in both local and standalone modes, and ensure CORS is properly configured for frontend development.
