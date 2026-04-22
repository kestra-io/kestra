# Aspire AppHost status for Kestra

## Files

- `AppHost.java` - Java AppHost for running Kestra under Aspire from the local source tree
- `aspire.config.json` - Java AppHost configuration and Aspire package references
- `ASPIRE_APPHOST_STATUS.md` - this status note

## Resources created

- `postgres` - PostgreSQL 18 using the built-in Aspire PostgreSQL integration
- `kestra-db` - the `kestra` PostgreSQL database resource
- `kestra-build` - a transient executable resource that runs `./gradlew writeExecutableJar --no-daemon`
- `kestra` - a source-backed executable resource that launches `build/executable/kestra-2.0.0-SNAPSHOT`

## What was achieved

- The final AppHost is written in **Java**, matching the Kestra repository language.
- Kestra runs from the **local clone/source**, not from a pre-published application container.
- The AppHost builds Kestra automatically before startup by making `kestra` wait for `kestra-build` to complete successfully.
- PostgreSQL credentials are modeled with Aspire parameters, and the password is marked as a secret.
- The PostgreSQL JDBC connection uses Aspire-resolved references from the PostgreSQL resource instead of a hard-coded connection string.
- Aspire OTLP wiring is enabled with `withOtlpExporter()`, so telemetry is sent to the Aspire dashboard endpoints during local runs.
- Kestra health is checked on the management endpoint.

## Important implementation details

- The generated Kestra executable is a shell-prefixed self-run launcher, so the AppHost runs it through `/bin/sh`.
- The launcher expects `build/executable/confs/` to exist before startup.
- Kestra must define both `datasources.default` and `datasources.postgres` in its generated configuration.
- The AppHost binds Kestra's HTTP and management ports from Aspire-assigned endpoint environment variables.

## Limitations

- The remaining known limitation is an **Aspire Java/polyglot limitation** for self-endpoint expression resolution on the same executable resource.
- A direct self-endpoint expression attempt such as `ReferenceExpression.refExpr("http://%s/", kestra.getEndpoint("http"))` was passed through literally instead of resolving to the running endpoint.
- Because of that limitation, `kestra.url` still uses `http://localhost:${KESTRA_HTTP_PORT}/` rather than a fully resolved Aspire self-endpoint expression.
- This harness is intended for **local development/run mode**. The transient `kestra-build` step and `waitForCompletion(...)` behavior are local-run concerns, not deployment-manifest behavior.
