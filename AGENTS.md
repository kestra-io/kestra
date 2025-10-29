# Kestra AGENTS.md

Hey there! This file is my go-to guide for getting AI coding agents up and running on the Kestra project. Kestra's this awesome open-source platform for data orchestration and scheduling, built with Java (Micronaut) and Vue.js. I've spent way too much time figuring this stuff out, so hopefully this saves you some headaches.

## Table of Contents

- [Repository Layout](#repository-layout)
- [Development Environment](#development-environment)
  - [Prerequisites](#prerequisites)
  - [Quick Setup with Devcontainer](#quick-setup-with-devcontainer)
  - [Manual Setup](#manual-setup)
- [Configuration Files](#configuration-files)
- [Running the Application](#running-the-application)
- [Building and Testing](#building-and-testing)
- [Development Guidelines](#development-guidelines)
- [Testing Strategy](#testing-strategy)
- [Plugin Development](#plugin-development)
- [Troubleshooting](#troubleshooting)
- [Pull Request Guidelines](#pull-request-guidelines)
- [Useful Commands](#useful-commands)
- [Getting Help](#getting-help)
- [Environment Variables](#environment-variables)

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

Look, if you're new to devcontainers or just want to skip the hassle of installing everything locally, this is your jam. The devcontainer does all the heavy lifting for you – no more wrestling with Java, Node, or whatever else.

#### Prerequisites

Before we jump in, let's make sure you've got the basics covered:

- **VS Code**: If you don't have it, grab it from [code.visualstudio.com](https://code.visualstudio.com/). Trust me, it's worth it.
- **Docker Desktop**: Head over to [docker.com](https://www.docker.com/products/docker-desktop), download it, and get it running. You'll see that little whale icon in your tray.
- **Remote Development Extension Pack**: In VS Code, hit Extensions (Ctrl+Shift+X), search for "Remote Development" by Microsoft, and snag the whole pack. It includes Dev Containers and a bunch of other goodies.

#### Step-by-Step Setup

1. **Fire up VS Code**: Open it and double-check Docker Desktop is humming along in the background.

2. **Extensions check**: Make sure the Dev Containers extension is there. If not, install it from the Extensions tab.

3. **Grab the project**: Go to `File > Open Folder...` and pick the Kestra root folder – the one with this AGENTS.md file.

4. **Launch the dev container**: Smash `Ctrl+Shift+P` for the command palette, type "Dev Containers: Open Folder in Container...", and hit enter. Pick the Kestra folder when it asks.

5. **Grab a coffee**: First time? It'll pull the container image and build the project. Could take 10-20 minutes depending on your setup. The Gradle build kicks off automatically.

6. **You're good to go**: Once it's done, you're in the devcontainer world with everything ready. Start coding!

For a visual guide, check out VS Code's [quick start docs](https://code.visualstudio.com/docs/devcontainers/containers). If there's a video, I'll link it here.

### Manual Setup

If you prefer to set things up yourself or can't use devcontainers for some reason, here's how to do it the old-fashioned way. It's a bit more work, but you'll have full control.

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

## Troubleshooting

Alright, let's talk about  I've dealt with setting this up. These are the issues that pop up most often, and yeah, they've bitten me more than once. If your problem isn't here, definitely check out the [GitHub issues](https://github.com/kestra-io/kestra/issues) or hop into [Slack](https://kestra.io/slack) – the community there is super helpful.

### Q: JavaScript Heap Out of Memory Error

**A:** Oh man, this one sneaks up on you when you're building the frontend and it has a ton of dependencies. I've had to bump up the Node.js heap size more times than I can count. Just set this environment variable:

```bash
export NODE_OPTIONS=--max-old-space-size=4096
```

Throw it in your `~/.bashrc` or `~/.zshrc` so it's always there. On Windows? Stick it in your environment variables.

### Q: CORS Errors in Browser Console

**A:** "CORS policy" errors driving you nuts when the frontend tries to chat with the backend? I've been there. Here's what usually fixes it:

1. Crack open your `cli/src/main/resources/application-override.yml` file.
2. Make sure CORS is flipped on like this:

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

3. Give the backend a restart: `./gradlew runLocal`

If your frontend's running on a different port, just toss that into the `allowedOrigins` list too.

### Q: Database Connection Issues

**A:** PostgreSQL giving you the cold shoulder? This has tripped me up plenty. Try these:

- **Inside a devcontainer?** Swap `localhost` for `host.docker.internal` in your connection string. Something like: `jdbc:postgresql://host.docker.internal:5432/kestra`
- **Is Postgres even running?** Quick check: `docker ps | grep postgres`
- **Credentials match?** Double-check your config against what's in the database.
- **Docker Compose drama?** Make sure the network's set up right between containers.

### Q: Gradle Build Failing

**A:** Builds acting up? This is my least favorite. Here's what I usually check:

- **Clear the cache**: `./gradlew clean` – wipes the slate clean.
- **Java version?** Gotta be 21+: `java -version`
- **Dependencies acting weird?** Try `./gradlew dependencies --refresh-dependencies`
- **Last resort**: Nuke the `.gradle` folder in your project root and start over.

Still stuck? Run `./gradlew build --info` for the full story.

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
| `MICRONAUT_ENVIRONMENTS` | Comma-separated list of Micronaut environments to activate (e.g., for custom configs) | `local,override` |
| `KESTRA_PLUGINS_PATH` | Directory where custom plugin JARs are loaded from at startup | `/workspaces/kestra/local/plugins` |
| `NODE_OPTIONS` | Options passed to Node.js runtime (useful for increasing memory limits) | `--max-old-space-size=4096` |
| `JAVA_HOME` | Path to the JDK installation directory | `/usr/java/jdk-21` |
| `GRADLE_OPTS` | JVM options for Gradle builds (e.g., heap size, proxy settings) | (none) |

Remember: Always test your changes in both local and standalone modes, and ensure CORS is properly configured for frontend development.
