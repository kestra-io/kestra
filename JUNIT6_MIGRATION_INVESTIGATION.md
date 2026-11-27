# JUnit 6 Migration Investigation

## 1. Current JUnit dependency status

The Kestra root repository resolves JUnit 6.1.3 through the

Micronaut Platform 5.2.2 and Micronaut Test BOM 5.2.0.

Dependency resolution was checked in the following modules:

- tests

- core

- jdbc-h2

- jdbc-mysql

- jdbc-postgres

- webserver

- scheduler

All resolved JUnit Jupiter dependencies to 6.1.3.

## 2. Custom extension compatibility

Kestra provides a custom annotation, `@KestraTest`, which registers

`KestraTestExtension` through Jupiter's `@ExtendWith`.

`KestraTestExtension` extends

`MicronautJunit5Extension` and overrides:

- buildMicronautTestValue

- getStore

- hasExpectedAnnotations

- beforeAll

- afterTestExecution

The resolved Micronaut Test 5.2.0 class exposes these methods with

matching signatures.

The extension uses standard Jupiter APIs, including:

- ExtensionContext

- ExtensionContext.Store

- ExtensionContext.Namespace

- AnnotationSupport

- BeforeAllCallback

- AfterTestExecutionCallback

No obvious JUnit 6 API incompatibility was identified during source

and signature inspection.

## 3. Legacy JUnit usage

A repository search for common JUnit 4 dependencies and APIs found

no matches for:

- junit:junit

- junit-vintage

- org.junit.runner

- org.junit.rules

- @RunWith

- @BeforeClass

- @AfterClass

## 4. Test validation

The `:tests:test` task completed successfully:

- 8 tests passed

- 0 failures

- 0 skipped

The `:core:test` task reported failures, but the observed failures

were not established to be caused by JUnit 6.

## 5. Plugin impact

The external plugin-amqp repository was examined as a representative
plugin consumer.

- It uses Kestra platform version 1.3.39.
- Its test runtime resolves JUnit Jupiter 5.14.0.
- It imports and uses the shared @KestraTest annotation in multiple
  test classes.
- No plugin-specific JUnit extension implementation was found in
  the inspected source files.

The plugin test suite reported 59 tests: 32 passed, 25 failed,
and 2 skipped. The observed failures included AMQP broker connection
refusals at localhost:5673 and execution timeouts.

These failures do not establish a JUnit compatibility problem.

This is a representative check, not a complete audit of all
external plugins.

## 6. OpenRewrite migration recipe

The official recipe is:

`org.openrewrite.java.testing.junit6.JUnit5to6Migration`

Artifact:

`org.openrewrite.recipe:rewrite-testing-frameworks`

An attempt to execute the recipe through a temporary Gradle init

script failed because the OpenRewrite plugin's transitive BOM

could not be resolved from the configured repositories.

The recipe was therefore identified but not successfully executed.

## 7. Conclusion

The root repository already resolves JUnit 6.1.3, and no obvious

incompatibility was found in the custom extension's inspected API

usage.

Before a full migration is declared complete, external plugin

compatibility and runtime behavior should be validated, and the

OpenRewrite recipe should be executed in an environment where its

dependencies can be resolved.
