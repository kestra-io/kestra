# Coding Agent Guidelines for Kestra Open Source Edition

This document provides essential information for AI coding agents working on the Kestra codebase.

**IMPORTANT — READ FIRST**

- **Act as a Senior Software Engineer and Software Architect.** Approach software development with:
    - **Pragmatism**: Favor simple solutions over clever ones
    - **Skepticism**: Question decisions that could cause technical debt or scalability issues
    - **Efficiency**: Only challenge when it genuinely matters
- **Think before coding**: explicitly state assumptions, compare alternatives, and justify choices.
- **Simplicity first (KISS)**: overengineering and "gas factories" are strictly forbidden.
- **Surgical changes only**: touch **only** what is strictly necessary to achieve the goal.
- **Goal-driven execution**: define what success looks like *before* writing the first line of code.
- **Preserve existing comments**: never delete any existing comment **unless** you are improving its clarity or usefulness.
- **Write clear, maintainable, and well-documented code**
- **Build & test are mandatory**

## Project

Monorepo built with Java (backend) and Vue (frontend), using Gradle as the build system.

## Tech Stack
- **Backend:** Java 25, Micronaut Framework, Lombok
- **Frontend:** Vue 3, TypeScript, Vite, Element Plus, Pinia
- **Build:** Gradle 8.x with multi-project structure (77 submodules)
- **Testing:** JUnit 5, Mockito, AssertJ, Vitest, Playwright

## Critical Code Patterns

### Dependency Injection

**DO**: Use constructor injection with final fields.

```java
@Singleton
public class MyService {
    private final SomeDependency dependency;

    @Inject
    public MyService(SomeDependency dependency) {
        this.dependency = Objects.requireNonNull(dependency);
    }
}
```

**DON'T**: Use field injection (`@Inject` on fields directly). Always prefer constructor injection.

### Class Structure

```java
// 1. Package declaration and imports
// 2. Class-level annotations (@Slf4j, @Singleton, etc.)
// 3. Class declaration with Javadoc
// 4. Static constants (UPPER_SNAKE_CASE)
// 5. Injected fields (@Inject)
// 6. Constructors
// 7. Public methods
// 8. Protected methods  
// 9. Private methods
// 10. Inner classes/records
```

### Annotations
- **Micronaut:** `@Singleton`, `@Inject`, `@Controller`, `@Replaces`, `@Requires`
- **Validation:** `@Valid`, `@NotNull`, `@Nullable`
- **Lombok:** `@Slf4j`, `@Getter`, `@NoArgsConstructor`, `@AllArgsConstructor`
- Use `@Builder` for complex object creation

### Error Handling

**DO**:
- Use specific exception types — extend `KestraException` or `KestraRuntimeException`
- Use `Optional<T>` for potentially absent returned values
- Return empty collections (e.g., `List.of()`, `Collections.emptyList()`) for absent values
- Use try-with-resources for resource management
- Log errors before re-throwing: `log.error("message", exception)`

**DON'T**: Use generic `Exception`. Don't return null for collections.

### Java Language Features
- Use java records for simple data carriers

### Naming Conventions
- Follow Java naming-convention best practices for Classes, Methods, Variables, Constants.
- Boolean methods: Start with `is`, `has`, `should`, `can` (e.g., `isReadOnly()`).

### File Organization
- Use 4-space indentation (configured in .editorconfig)
- UTF-8 encoding with LF line endings
- No trailing whitespace

### Utility Classes
* Mark utility classes as `final` with a private constructor
* Use static methods only
* Use existing utility classes (e.g., `ListUtils`, `MapUtils`) instead of creating new ones (`io.kestra.core.utils.*`)

### Enums
- Use enums for fixed sets of constants
- Use `@JsonValue` for custom serialization if needed
- Use `UNKNOWN` enum value for unknown cases in deserialization
- Compare Constants From The Left (a.k.a., Yoda conditions)
- Use a static `fromString` method for case-insensitive lookups using `Enums` class.

e.g.:
```java
public enum MyEnum {
    VALUE_ONE,
    VALUE_TWO,
    UNKNOWN;

    @JsonCreator
    public static ResourceType fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, MyEnum.class, UNKNOWN);
    }
}
```

### Documentation
- Javadoc for all public classes and methods - be concise
- Use `@param`, `@return`, `@throws` appropriately
- Use `{@inheritDoc}` for inherited methods
- Include usage examples for complex methods

## Webserver Constraints
- Put classes used by only controllers in the webserver module (not core)
- No business code/rule inside controllers - instead use a Service class
- All APIs must return a valid JSON object
- APIs should not return a response being a JSON array which cannot be evolved in a backwards-compatible way
- Unit tests must assert that a user can only access a given API if authorized to do so, and that access is denied otherwise
- APIs must be documented with OpenAPI annotations
- Use DTOs for requests/responses
- Always validate input parameters with `@Valid`
- Use `@ExecuteOn(TaskExecutors.IO)` for blocking operations
- Return meaningful error responses in controllers

## Worker Constraints
- Never depend on repositories for code called by the workers - instead use MetaStore/StateStore facades

## Testing Guidelines

### Java Tests

**Test Organization:**
- Test classes end with `Test` (e.g., `SecretServiceTest`)
- Abstract test classes start with `Abstract`
- Use `@KestraTest` for Kestra-specific integration test configuration
- Use `@MicronautTest` only when `@KestraTest` is not appropriate
- Place tests in same package structure as source code
- Simple unit test with mocks over complex integration tests when possible
- Add // Given-When-Then comments for clarity

**DO**:
- Always use naming conventions for test methods (e.g., `shouldPerformActionWhenCondition`)
- Use `@KestraTest` for tests that require running Kestra services (e.g., Executor, Scheduler)
- 
```java
@KestraTest
class ServiceTest {
    @Inject
    private ServiceClass service;
    
    @Test
    void shouldPerformActionWhenCondition() {
        // Given (setup)
        
        // When (action)
        
        // Then (assertions)
        assertThat(result).isNotNull();
    }
}
```

**DON'T**: Use Nested classes for test organization. Avoid complex test hierarchies.

**Assertions:**
- Use AssertJ: `assertThat().isEqualTo()`, `assertThat().isNotNull()`, `assertThatThrownBy()`, `assertThatObject()`
- Prefer descriptive assertion methods
- Use `@MockBean` for mocking dependencies

**Test Categories:**
- Unit tests: Fast, isolated, no external dependencies
- Integration tests: Test component interaction, use `@Tag("integration")`
- Flaky tests: Use `@Tag("flaky")` for unreliable tests

### Frontend Tests
- Unit tests with Vitest and `@vue/test-utils`
- E2E tests with Playwright
- Storybook component tests
- Use JSdom environment for DOM testing

## Frontend Code Style (Vue 3)

**File Organization:**
- Use 2-space indentation for Vue, JSON, YAML, CSS
- Use 4-space indentation for JavaScript/TypeScript
- Follow Vue 3 Composition API patterns
- Organize imports: Vue/framework → third-party → local modules

**Naming Conventions:**
- Components: `PascalCase` files (e.g., `MyComponent.vue`)
- Variables/functions: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- CSS classes: Follow Element Plus conventions

**TypeScript:**
- Use strict TypeScript configuration
- Prefer type definitions over `any`
- Use interfaces for object shapes
- Use enums for fixed sets of values

## Build Commands

### Java Backend

```bash
# Clean build
./gradlew clean

# Full build (includes tests)
./gradlew build

# Build without tests (faster)
./gradlew build -x test -x integrationTest -x testCodeCoverageReport --refresh-dependencies --no-daemon --parallel

# Create executable JAR
./gradlew executableJar --parallel

# Run application locally
./gradlew runLocal

# Run standalone server
./gradlew runStandalone
```

### Test Commands

```bash
# Run all tests (excludes flaky tests)
./gradlew test

# Run only unit tests (fastest)
./gradlew unitTest

# Run integration tests
./gradlew integrationTest

# Run flaky tests (separate from build)
./gradlew flakyTest

# Run tests for specific module
./gradlew :core:test

# Run single test class
./gradlew :module-name:test --tests "ClassName"

# Run single test method
./gradlew :module-name:test --tests "ClassName.methodName"
```

### Frontend (UI)

```bash
cd ui

# Install dependencies
npm install

# Development server
npm run dev

# Type checking
npm run check:types

# Build for production
npm run build

# Run tests
npm run test:all        # All tests with coverage
npm run test:unit       # Unit tests only
npm run test:storybook  # Storybook tests
npm run test:e2e        # End-to-end tests

# Linting
npm run lint            # Fix linting issues
npm run test:lint       # Check linting only

# Storybook
npm run storybook       # Development
npm run build-storybook # Build
```

## Development Workflow

### Running Locally

1. **Start/stop backends:**
```bash
# Start databases with Docker Compose
docker compose -f docker-compose-ci.yml up

# Stop databases with Docker Compose
docker compose -f docker-compose-ci.yml down
```

2. **Access application:** http://localhost:8080

### Security Considerations
- Use tenant isolation for multi-tenant features
- Implement proper authorization with `@HasAnyPermission`
- Handle secrets securely (never log sensitive data)

### Performance Best Practices
- Implement pagination for large datasets
- Use streaming for large file operations
- Cache frequently accessed data appropriately
- Initialize collections with the expected size to avoid resizing overhead

## Troubleshooting

**Common Issues:**
- **Build failures:** Run `./gradlew clean` and retry
- **Test failures:** Check for service dependencies (Docker containers)
- **Frontend issues:** Ensure Node.js version matches package.json requirements

**Debugging:**
- Use IDE debugging with remote JVM debugging
- Use Micronaut's built-in health endpoints
- Enable debug logging: `--logging.level.io.kestra=DEBUG`
- Use JUnit and Vitest reports for test failures

## Module Structure

**Core Modules:**
- `cli` - Command Line Interface
- `core` - Core functionality
- `webserver` - Web server
- `ui` - Vue 3 frontend application
- `executor` - The component responsible for managing execution state
- `scheduler` - The component responsible for scheduling polling and schedule triggers
- `worker` - The component that executes tasks and manages worker instances
- `worker-controller` - The component that manages worker instances and job distribution
- `indexer` - The component responsible for indexing executions
- `plateform` - provides the Platform Bill of Materials (BOM) for dependency management

**Queuing Layer:**
- `queue` - Core API for queue implementations
- `queue-jdbc` - JDBC-based queue implementation
 
**Data Layer:**
- `jdbc-*` - Database implementations (H2, Postgres, MySQL)

**Testing Modules:**
- `tests` - Common test utilities and base classes
- `jmh-benchmark` - JMH benchmarks for performance testing

**Key Patterns:**
- Repository pattern for data access
- Service layer for business logic
- Controller layer for HTTP endpoints
- Builder pattern for object construction (often with Lombok `@Builder`)

## Pull request guidelines
- Always add tests, keep your branch rebased instead of merged, and adhere to the commit message recommendations from https://www.conventionalcommits.org/en/v1.0.0.
- Use types: chore, feat, fix, refactor, test, docs, build
- Use scopes: apps, assets, core, dashboards, deps, executions, flows, iam, namespaces, plugins, secrets, storage, scheduler, system, tasks, tenants, tests, triggers, variables, version, worker

This document should be updated as the codebase evolves. When in doubt, follow existing patterns in the codebase and maintain consistency with established conventions.
