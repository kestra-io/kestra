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
- **Reuse before writing**: the pattern you need almost always exists already. Find the closest equivalent in the codebase and follow it instead of inventing a second way to do the same thing.
- **No comments by default**: see [Comments](#comments). Over-commenting is one of the most common reasons a PR written by an agent has to be revised.
- **How Kestra looks is decided by the design system**: see [UI Design System](#ui-design-system). No color, spacing, radius or component pattern may be invented in feature code.
- **A test has to be able to fail for a reason a reviewer would care about**: see [When a test earns its place](#when-a-test-earns-its-place). More tests do not make a change safer.
- **Build and test only what you touched** rather than the whole repo: see [What to run after a change](#what-to-run-after-a-change).

## Comments

**The default is no comment.** Small methods with accurate names need no explanation, and every comment is a second thing that has to be kept in sync with the code. Over-commenting is easy to slip into: the next line gets narrated, the method name gets restated above the method, and `// Step 1` / `// Step 2` scaffolding is left behind. None of that will be accepted in review.

The reader to write for is the developer opening the file in a month. They do not have time to read a paragraph, and most of what a paragraph would tell them is already in the code.

Write a comment only when a reviewer would otherwise have to ask **why** the code exists:

- a non-obvious constraint, or an upstream bug being worked around (link the issue)
- an ordering, locking or concurrency requirement that the code itself cannot express
- a deliberate omission, so that the next person does not "fix" it back

Keep it to **one sentence**. When a paragraph seems necessary, make the code clearer instead.

Never write:

- a restatement of the line below it (`// increment the counter`, `// return the result`)
- a narration of a self-explanatory getter, loop, or well-named call
- section banners (`// ---- helpers ----`), changelog notes, or a `// TODO` with no issue link
- a comment on every entry of a list when the rule is already stated once above the list

**Existing comments** should be left alone unless the code they describe changed. When that code is deleted, delete the comment with it rather than rewording it around the gap. When the code changed, either correct the comment or remove it.

**Javadoc** describes the contract rather than the body. Add it on public API called from other modules or from plugins, and on anything whose behavior cannot be seen from the signature (nullability, thread-safety, side effects, units, ownership of a returned collection). Skip it on getters, setters, builders, an override that adds nothing (a bare `{@inheritDoc}` is noise), and any method whose name already says everything.

## Project

Monorepo built with Java (backend) and Vue (frontend), using Gradle as the build system.

## Tech Stack
- **Backend:** Java 25, Micronaut Framework, Lombok
- **Frontend:** Vue 3, TypeScript, Vite, Element Plus, Pinia
- **Build:** Gradle, multi-project. The version is pinned in `gradle/wrapper/gradle-wrapper.properties`, and `settings.gradle` is the list of modules
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
- Write exception messages as plain, complete sentences that state the fact and the actionable detail — build them with `String.formatted()`/`String.format()`, not string concatenation or em dashes, e.g. `"Cannot acquire lock on asset '%s': already locked by '%s' until %s.".formatted(id, owner, until)`

**DON'T**: Use generic `Exception`. Don't return null for collections. Don't write terse or telegraphic exception messages (e.g. dropping articles/verbs) or string-concatenate message parts.

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

**MANDATORY — never hand-roll Pebble delimiter detection.** Pebble has two block delimiter pairs — print blocks (`{{ ... }}`) and execute/statement blocks (`{% ... %}`) — and code that only checks for `{{`/`}}` silently misses `{%`/`%}` blocks. Use `io.kestra.core.utils.PebbleUtil` (`containsOpeningBlockDelimiter`, `startsWithOpeningBlockDelimiter`, `endsWithClosingBlockDelimiter`, `openingBlockDelimiters()`/`closingBlockDelimiters()`) instead of writing a new delimiter regex or literal — it derives the delimiter pairs from Pebble's own `Syntax.Builder` defaults, so it never drifts from what Pebble actually parses.

### Enums
- Use enums for fixed sets of constants, including internal fields not exposed over the API — prefer a typed enum over a raw `String`/`int` whenever the value is drawn from a closed set of known cases, even if the set may only ever have a couple of members
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

See [Comments](#comments) for when a Javadoc block is worth writing at all. Once you have decided that it is:

- Keep it to the contract: what the caller has to know that the signature does not already say
- Use `@param`, `@return` and `@throws` only where each adds something. `@param name the name` is noise
- Skip a bare `{@inheritDoc}` on an override that changes nothing
- Add a usage example only when the call site would otherwise be guessed wrong

## Webserver Constraints
- Put classes used by only controllers in the webserver module (not core)
- No business code/rule inside controllers - instead use a Service class
- All APIs must return a valid JSON object
- APIs should not return a response being a JSON array which cannot be evolved in a backwards-compatible way
- Any endpoint returning a collection should be paged through `PagedResults` with `page` and `size` parameters, like the rest of the API
- A DTO used by a single controller belongs as an inner record of that controller. Promote it to `io.kestra.webserver.models.api` only once a second caller exists, and name it with the `Api` prefix there (`ApiExecution`, `ApiTriggerState`, `ApiPluginArtifact`)
- Import a class rather than writing its fully-qualified name, as soon as it is used more than once in the same file
- Unit tests must assert that a user can only access a given API if authorized to do so, and that access is denied otherwise
- APIs must be documented with OpenAPI annotations
- Use DTOs for requests/responses
- Always validate input parameters with `@Valid`
- Use `@ExecuteOn(TaskExecutors.IO)` for blocking operations
- Return meaningful error responses in controllers

## Worker Constraints
- Never depend on repositories for code called by the workers - instead use MetaStore/StateStore facades

## Executor Constraints
- Run the `H2RunnerTest` whenever you update part of the executor

## Testing Guidelines

### When a test earns its place

More tests do not make a change safer. Every test is code that has to be read, kept passing, and understood by whoever changes that behavior next, so a test is only worth its cost when it can fail for a reason a reviewer would care about.

**Add a test when:**

- new behavior is introduced, or a bug is fixed. The test should fail on the commit before yours; if it passes there, it is testing something else
- an edge case would be easy to get wrong later: an empty collection, a null, a boundary, a race, a permission check
- the change is in the executor, the scheduler, the queue, or an ACL path, where a regression is expensive and hard to notice

**Don't add a test when:**

- it only exercises getters, setters, builders, or a Lombok-generated method
- it asserts behavior that the framework already documents (Jackson serializing a field, Micronaut injecting a bean)
- it repeats an existing test with one property changed. One test per behavior, rather than one per property, since otherwise there would be hundreds of them
- the mocks would have to restate the implementation for the assertions to pass. Such a test is pinned to the current code rather than to the behavior, so it is broken by every legitimate refactor
- it exists only to move a coverage number

**Delete tests too.** Removing a feature should reduce the test count rather than raise it. When a test no longer describes behavior anyone relies on, or when it duplicates another one, delete it in the same PR and say so in the description. A redundant test that is always green is still maintenance cost.

### Java Tests

**DO**:
- Place tests in same package structure as source code
- Simple unit test with mocks over complex integration tests when possible
- Structure the body as given / when / then. The three marker comments are optional and should be dropped when the test is only a few obvious lines
- Test method naming: `should<ExpectedBehavior>When<ConditionOrAction>` (also `...Given<Input>`, `...For<Condition>`, `...If<Condition>`), e.g. `shouldThrowExceptionWhenDividingByZero()`
- Use `@MicronautTest` for tests that require Micronaut beans
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
- **Prefer Storybook component tests over Vitest unit tests whenever possible** — components render through their real story setup (props, slots, design-system deps) instead of being stubbed out, catching regressions unit mocks miss. Fall back to a Vitest unit test only when the logic under test isn't component-rendering behavior (e.g. a pure helper/composable) or no story exists and adding one isn't practical.
- Don't keep a Vitest test that only re-asserts what a story already covers. Delete it when you add the story.
- Assert on `data-test` attributes and rendered text. A test written against `.el-*` or `.ks-*` class names is broken by the next Element Plus or design-system upgrade, without any behavior having changed.

## UI Design System

The design system at [ui/packages/design-system/](ui/packages/design-system/) is the **single source of truth for every design decision** in Kestra: colors, typography, spacing, radii, shadows, and the component vocabulary. Nothing rendered to a user may be styled outside it.

In practice that means no hex codes, no `rgb()`, no `--el-*` or `--bs-*` variables, no hardcoded pixel spacing, no `:deep()` into another component, and no locally built equivalent of a component that already exists. When the design you need cannot be expressed with the existing `Ks*` components and `--ks-*` tokens, that is a gap in the design system: raise it with design and add the token, the prop or the component upstream. Approximating it with custom CSS in a feature component is not an option, since the result will only be right in the one theme you checked it in.

The full rules, the component catalogue, the token reference and the frontend best practices live in [ui/AGENTS.md](ui/AGENTS.md), which is auto-loaded for work under `ui/` in OSS and `ui-ee/` in Enterprise Edition. Read it before any frontend change, and update it in the same PR whenever a component, a prop or a token is added.

@ui/AGENTS.md

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

### What to run after a change

A whole-repo `./gradlew build` can take tens of minutes, and almost nothing about a three-line change can be learned from it. Start with the narrowest command that covers what you touched, and widen it only once that one passes.

| What changed | Run |
|---|---|
| One backend module | `./gradlew :<module>:test --tests "TheClassName"`, then `./gradlew :<module>:test` |
| Anything in the executor | `./gradlew :jdbc-h2:test --tests "H2RunnerTest"` (mandatory) |
| Something crossing module boundaries | `./gradlew build -x integrationTest` |
| A Vue component or composable | `cd ui && npm run check:types && npm run test:unit && npm run lint` |
| A design-system component | the above, plus `npm run test:storybook` |
| `en.json`, whether a key was added or a value edited | `cd ui && npm run translations:generate && npm run translations:check` |
| A controller or its DTOs | that module's tests, including the authorization tests for the route |

### Java Backend

```bash
# Clean build
./gradlew clean

# Full build (includes tests)
./gradlew build

# Build without tests (faster)
./gradlew build -x test -x integrationTest -x testCodeCoverageReport --refresh-dependencies --no-daemon --parallel
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

# After running tests: generate a markdown summary of failures only
npx --yes @kestra-io/kestra-devtools generateTestReportSummary --only-errors $(pwd)
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

### Worktree setup

When working in an OSS worktree (the working directory is under a `worktrees/` directory):
```bash
dev-tools/setup-worktree.sh ../worktrees/foo
```
This copies the gitignored `cli/src/main/resources/application-*.yml` files from the main checkout into the worktree. Without this step Kestra cannot boot in the worktree. The script is idempotent — safe to re-run.

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

- **A Gradle build fails with no visible cause:** run `./gradlew clean` and retry once. When it repeats, read the actual error instead of retrying again.
- **Tests fail on connection errors:** the databases are not running. `docker compose -f docker-compose-ci.yml up -d`.
- **The frontend build fails on dependency resolution:** check the Node and npm versions against `package.json` engines before touching anything else.
- **`npm install` fails with `EBADENGINE`:** the UI workspaces require npm >= 11.7 and set `engine-strict=true`. Older npm mislabels the `sass`/`sass-embedded` platform binaries as peer dependencies and rewrites `package-lock.json` on every install. Install the pinned npm (`npm i -g npm@11.16.0`) or use the Node release named in `ui/.nvmrc`.
- **The UI builds but the app never mounts:** module federation, so the cause is usually workspace resolution rather than the change itself. Verify against a running instance rather than against the dev server.
- **Debug logging:** `--logging.level.io.kestra=DEBUG`.

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
- `platform` - provides the Platform Bill of Materials (BOM) for dependency management

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

- **One PR, one scope.** When you cannot pick a single conventional-commit scope for the title, the PR is doing too much and should be split.
- Title format `type(scope): lowercase description`, following https://www.conventionalcommits.org/en/v1.0.0. Name the changed things rather than the actions taken on them: `fix(cases): assignees header, missing Acknowledged status, auto-link toggle`.
- Use types: chore, feat, fix, refactor, test, docs, build
- Use scopes: apps, assets, core, dashboards, deps, design-system, executions, flows, iam, namespaces, plugins, secrets, storage, scheduler, system, tasks, tenants, tests, topology, triggers, variables, version, worker
- Put the closing keyword on the first line of the description, as a full URL so that the link is not lost in a squash merge: `Closes https://github.com/kestra-io/kestra/issues/<id>.`
- Cover new behavior with tests that could fail, following [When a test earns its place](#when-a-test-earns-its-place). "Add tests" never means one test per changed line.
- Run `npm run lint` in `ui/` before pushing any frontend change. Otherwise one comment per violation is posted by reviewdog, and the human review is buried under trailing-comma suggestions.
- Attach a screenshot or a short recording for every user-visible change, taken against a running instance.
- Keep the branch rebased on `develop` rather than merged.
- Fill in `.github/pull_request_template.md`, and delete the checklist section that does not apply instead of leaving it unticked.
- Write the description as problem, fix and evidence, once each. No table restating the diff, and no checklist of tests that all passed.
- Never commit generated output by hand: the twelve non-English translation files, `fingerprints.json`, and the generated SDK are produced by their scripts or by CI.

## Issue guidelines
- **Classify an issue with its GitHub issue type, not a `kind/*` label.** The `kind/bug` label is retired — do not add it. Set the type instead: `gh issue create --title … ` followed by `gh issue edit <number> --type Bug`, or `gh issue edit <number> --type Task|Feature|Epic`. Available types are `Task`, `Bug`, `Feature` and `Epic` (list them with `gh api /orgs/kestra-io/issue-types`).
- **Do add the `area/*` labels** — `area/frontend`, `area/backend`, `area/devops`, `area/docs`, `area/plugin`, `area/qa`, `area/analytics` — since those drive routing and are still in use.
- Leave triage labels such as `kind/cooldown` to `kestrabot`; it applies them automatically on new issues.

## UI Translations

**MANDATORY — never hardcode user-facing strings.** Every label, button, tooltip, placeholder, dialog/section title, table-column header, and toast/confirm message rendered to the user MUST go through vue-i18n: `t("key")` (or `:label`/`:tooltip` bindings) in components, and `<i18n-t keypath="...">` with named slots when the string embeds markup or a component (e.g. a `<code>` fragment). Never write a literal user-facing string in a template, a `:tooltip`/`:label` attribute, or a `toast.*` call. Reuse existing generic keys (`cancel`, `delete`, `edit`, `save`, `add`, `id`, `description`, `namespace`, `revision`, …) instead of duplicating them; put feature-specific strings under one namespaced object (e.g. `"reusableInputs": { … }`). After adding keys to `en.json`, propagate them to every language (translation generation script) so the missing-keys check stays clean — a key present only in `en.json` fails the check.

Translation files live in `ui/src/translations/`. There is one JSON file per language code (e.g. `de.json`, `fr.json`) plus the source `en.json`.

### Checking for missing translations

Run the check script from the `ui/` directory:

```bash
cd ui && npm run translations:check
```

A clean run reports `No missing keys.`, `No extra keys.` and `No stale keys.` for every language. Anything listed must be fixed before merging — the same check runs as a PR gate.

> **Enterprise Edition:** EE-only keys live in `ui-ee/src/translations/ee_translations/en.json` and are checked separately — run `npm run translations:check` in `ui-ee` as well (see `kestra-ee/AGENTS.md` → "Frontend i18n").

### Editing English strings

**Changing an existing English value is a translation change.** Every key carries a fingerprint of the English text its translations were generated from, so editing `en.json` — even just the capitalisation — marks that key stale in all twelve languages and fails `translations:check` until it is regenerated. Run `npm run translations:generate` and commit the result alongside your change.

This is deliberate: before it existed, edited values were never propagated, and a rename of "SuperAdmin" to "Superadmin" sat un-translated in eleven locales for a year (kestra-io/kestra#10656).

### Adding or regenerating translations

Prefer `npm run translations:generate` (needs `GEMINI_API_KEY`); it fills missing keys and re-translates stale ones on its own, with no flag to remember. Pass `true` to force a full re-translation of everything.

If you must write a translation by hand:

1. Identify gaps by running `npm run translations:check`.
2. Follow these translation rules (mirroring `ui/scripts/translations/generateTranslations.ts`, the generator shared by OSS and EE):
   - **Reserved English terms — never translate:** `kv store`, `namespace`, `tenant`, `flow`, `subflow`, `task`, `log`, `blueprint`, `id`, `trigger`, `label`, `key`, `value`, `input`, `output`, `port`, `worker`, `backfill`, `healthcheck`, `min`, `max`.
   - **ALL-CAPS status labels stay in English:** `WARNING`, `FAILED`, `SUCCESS`, `PAUSED`, `RUNNING`, etc.
   - **Preserve `{placeholder}` variables** exactly — vue-i18n uses a **single** pair of braces. Do not translate the name inside the braces, do not rename it, and never write `{{placeholder}}`: double braces are a compile error (`Not allowed nest placeholder`) and make `t()` throw at render time. Each translation must carry exactly the same placeholders as the English source — no invented ones, none dropped.
   - **Use natural UI terminology** — avoid false friends or overly literal translations (e.g. German: Execution → Ausführung, Theme → Modus, State → Zustand).
3. Insert the translated keys into the correct position in the target language JSON, mirroring the key order of `en.json`.
4. Re-run `npm run translations:check` to confirm everything is clean before committing.

The tooling itself lives in `ui/scripts/translations/` and is shared with EE, which keeps only thin entry points. Rules live in `.mjs` so the dependency-free PR gate can apply them; file IO and orchestration stay in `.ts`.

### Conflicts in `fingerprints.json`

Two branches that both touch `en.json` will both regenerate `ui/scripts/translations/fingerprints.json`, so it conflicts often. **Never hand-merge the hashes and never pick a side** — a hash says "this English text is what the twelve translations were generated from", so choosing the wrong one silently marks a drifted key as current and the drift becomes invisible again.

Resolve it the same way as a `kestra-sdk` conflict — regenerate:

```bash
git checkout --ours ui/src/translations/*.json ui/scripts/translations/fingerprints*.json
cd ui && npm run translations:generate   # fills whatever the other branch added
npm run translations:check               # must report no missing / extra / stale keys
```

`en.json` itself normally merges cleanly, since branches usually add different keys; it is the generated files that collide.

## Keeping this file up to date

When a review comment corrects something that is not written here, and the correction is a rule rather than a one-off, add it in the same PR: one or two lines, with the example next to the rule. Delete a rule once it no longer matches the codebase. A guideline that no longer matches the code is worse than no guideline, since it will be followed confidently.

When in doubt, follow the existing patterns in the codebase and stay consistent with them.
