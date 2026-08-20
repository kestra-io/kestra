# Plugin Auto-Install & Schema Bundle

How Kestra offers editor autocompletion for plugins that are **not installed**, and how it
**auto-downloads** the missing plugin JARs when a flow is saved.

Two independent features, at two different moments:

| Feature | When | Payload | Needs the JARs? |
|---------|------|---------|-----------------|
| **Auto-completion** | while editing | schema JSON (a few MB) | **No** |
| **Auto-download / install** | on flow save | plugin JARs | Yes — only the missing ones |

They only share the plugin **catalog** (the group → Maven-artifact mapping).

---

## Motivation — why

The "batteries-included" distribution has hit a scaling wall. The plugin ecosystem grew from ~600
to 1,300+ plugins in a year, pushing the `kestra/kestra:latest` image past **2.6 GB**, and the
catalog keeps growing. The total image weight is fundamentally bound by the number of plugins
shipped:

> Total weight = core engine + (plugin₁ + plugin₂ + … + pluginₙ)

Per-JAR size optimizations cannot offset this linear growth. The cost lands as:

- **Infrastructure latency** — slow image pulls in CI/CD and Kubernetes.
- **Resource waste** — every user hosts gigabytes of plugin code they never run.

The fix is to shift from a "fat image" to **just-in-time acquisition**, so each user's footprint
becomes:

> User weight = core engine + (only the plugins actually used)

Since the actively-used set is a tiny fraction of the catalog, the practical footprint shrinks by an
order of magnitude while the full ecosystem stays reachable on demand. Autocompletion (feature 1)
keeps discovery working against the *whole* catalog even on a lean install; auto-download (feature 2)
acquires a plugin the moment a flow actually uses it.

### Target persona & scope

The target is the **OSS user evaluating Kestra on a local deployment** (`server local`) — spinning
Kestra up locally or on a single VM. The goal is zero friction between "just started Kestra" and
"first flow runs": no need to know which plugins exist, edit a Dockerfile, or restart anything. The
local plugins directory is the storage target.

Auto-download is therefore **on by default only for OSS + local-filesystem storage**, and off
everywhere else. The computed default is `edition == OSS && storage.type == local` — the storage
type is the discriminating signal because `server local` extends the standalone command and always
reports `ServerType.STANDALONE`, so the server type alone cannot tell a local dev instance from a
generic standalone deployment on S3/GCS. An explicit `kestra.plugins.auto-install.enabled` always
overrides it (so an operator can still opt in elsewhere), and `server local` also sets it to `true`
explicitly in its property overrides. These are deliberately **out of scope** and stay off by
default:

- **OSS distributed / production** — wants predictable, pre-provisioned plugin sets; on-demand fetch
  at save time is undesirable there.
- **Enterprise Edition** — plugin installation is a governed, cluster-level admin operation;
  auto-fetch on save would bypass that governance.

### Distribution: two images

The feature assumes a dual-image distribution (the build itself lives in the `actions` repo, out of
scope for this codebase):

- `kestra/kestra:<version>` — **full** image, all plugins bundled (offline / air-gapped use).
- `kestra/kestra:<version>-slim` — **slim** image, core engine only. This is the image that
  relies on the two features below to stay usable.

---

## Before / After

**Before.** The editor autocompleted only from the schema of **locally installed** plugins. A
task/trigger type whose JAR was not installed had no schema → no completion, and validation
flagged it as an unknown type. To get completion for a plugin you first had to install it
(download its JAR).

**After.** The editor autocompletes for **every plugin in the catalog**, installed or not, from a
lightweight pre-baked JSON schema — no JAR download involved. The JAR is fetched lazily, only when
the flow is saved, and only for the types actually referenced.

---

## Feature 1 — Auto-completion without the JARs (the "schema bundle")

The insight: completion needs the **shape** of a plugin (property names, types, descriptions), not
its **code**. The shape is JSON metadata; it can travel independently of the binaries.

### (a) Build time — bake the bundle (CI, once per release)

A new CLI command `plugins-schema` (`PluginsSchemaCommand`) runs on the CI runner, which has the
**full plugin set** installed (all plugin JARs — several GB, present only on the build machine). It:

1. Generates the schema for the 5 editor root types (flow, task, trigger, plugindefault, dashboard)
   via `JsonSchemaGenerator.schemas()`, reflecting over the full plugin registry.
2. Hoists every `definitions` entry into **one shared, deduplicated pool** (the 5 schemas overlap
   heavily) plus a small `roots` map of `SchemaType` → the `$ref` of that type's root class:

```json
{
  "definitions": { "io.kestra.plugin.core.log.Log": { ... }, ... },
  "roots": {
    "task":    "#/definitions/io.kestra.core.models.tasks.Task",
    "trigger": "#/definitions/io.kestra.core.models.triggers.AbstractTrigger",
    ...
  }
}
```

The output, `plugins-schema.json`, is a few MB of JSON, and it is **embedded in the Kestra JAR** —
there is no hosted copy and nothing to fetch. Release CI generates the bundle from the full plugin
catalog, then rebuilds the artifacts with `-PpluginsSchemaBundle=<path>`, which stages it as the
`/plugins-schema.json` classpath resource of `:cli` (see `cli/build.gradle`). It therefore ships with
every distribution built from that jar — the shadow jar, `java -jar`, the zip/tar install, the
executable and both Docker images — and is resolved with no network access at all.

Without the property the resource is simply absent (a plain `./gradlew build`, or any custom build),
and the service is a no-op unless one of the two overrides below is configured.

> **Not `oss.json`.** `oss.json` (from `configuration-schema`) describes the *instance
> configuration* (`application.yml` settings + storages). `plugins-schema.json` describes the
> *flow/task/trigger* vocabulary. Different generator, different content, different consumer — a
> separate file on purpose. They share only the CI transport (same job, bucket, versioned folder).

#### Wiring the embedding in release CI

The artifacts are built by `kestra-io/actions` (`kestra-oss-build-artifacts.yml`), not by a workflow
in this repository, so the embedding is a two-pass build there. It has to be: the bundle must cover
plugin types that are *not* build dependencies, so it can only be generated by running the
freshly-built executable against a full plugin catalog — which does not exist until the first pass
has produced that executable. The second pass re-runs `processResources` / `jar` / `shadowJar` only;
compilation is already up to date, so it costs a repackage, not a rebuild.

```yaml
# after the existing `./gradlew executableJar` step, and before the artifact uploads
- name: Download the full plugin catalog
  run: |
    kestractl plugins download "${{ github.ref_name }}" \
      --plugins-dir ./plugins-catalog \
      --plugins "${{ needs.plugins.outputs.plugins }}" \
      --concurrency 50

- name: Generate the plugin schema bundle
  run: |
    chmod +x build/executable/*
    build/executable/* plugins-schema --plugins ./plugins-catalog -o build/plugins-schema.json

# Repackage with the bundle staged as the /plugins-schema.json classpath resource.
- name: Gradle - Rebuild with the embedded bundle
  run: ./gradlew executableJar -PpluginsSchemaBundle=build/plugins-schema.json
```

Prerequisites for that job, which it does not have today: the `plugins` job as a `needs` dependency
(for the catalog list), `kestractl` on the `PATH`, and the `GCP_SERVICE_ACCOUNT` secret for the
Maven mirror — all three already exist in the sibling `docker` job of
`kestra-oss-publish-docker.yml` and can be lifted from there.

> **Do not reuse the `kestra-configuration-schema` composite action here.** It installs only the
> `io.kestra.storage` artifacts before running the command, because it was written for `oss.json`.
> A bundle generated through it covers core plus storage types — not the catalog of un-installed
> plugins the feature exists to complete. The steps above download the real catalog with
> `kestractl plugins download` for that reason.

The bundle is **not** published anywhere. `main-build.yml` / `pre-release.yml` upload only
`oss.json` (the instance-configuration schema) to GCS; the earlier step that also pushed
`plugins-schema.json` there was removed once the jar carried it, and
`kestra.plugins.schema-bundle-url-template` ships with no default as a result. A `develop` or
`-SNAPSHOT` build has no embedded bundle and no URL to fall back to, so catalog completion is simply
inactive there — as it effectively already was, since dev bundles were published under a `develop/`
prefix the stable-version URL never matched.

### (b) Runtime — fetch & cache

`PluginSchemaBundleService` resolves a single bundle source once, at construction, in priority
order (`resolveBundleSource`):

1. `kestra.plugins.schema-bundle-path` — an explicit local file. Highest priority so a developer
   (or `plugin-devtools`' `kestra-core-run`, which wires it as a `-D`) can point at a full-catalog
   bundle and always win over the JAR-bundled default.
2. the `/plugins-schema.json` classpath resource — the bundle embedded in the JAR by release CI.
   No network access, so completion works offline / air-gapped out of the box.
3. `kestra.plugins.schema-bundle-url-template` (`{version}` → current stable version) — a remote
   bundle you host yourself. No default: nothing is published for Kestra to fetch, so this is purely
   an escape hatch for custom builds that ship no embedded bundle.

When none of the three resolves the service is a **no-op**. The resolved source is cached ~1h and
loaded asynchronously. This is pure JSON — **no plugin JAR is ever downloaded here.**

### (c) Serve — merge on the fly

`GET /api/v1/plugins/schemas/{type}?includeCatalog=true` (`PluginController`) starts from the
**local** schema (installed plugins) and enriches it with the bundle via
`PluginSchemaBundleService.mergeWithBundle` — `mergeLightweightSubtypes`:

- for **every occurrence** of a polymorphic subtype list in the local schema, it adds, for each
  catalog subtype not already installed, a **lightweight definition** **and a `$ref` branch** to it
  in that occurrence's `anyOf`. The definition pins the discriminator
  (`{"type": "object", "properties": {"type": {"const": "<fqcn>"}}, "required": ["type", ...]}`,
  plus `title` / `markdownDescription` when the bundle carries them) and lists the plugin's other
  **property names as empty shells** — each keeps only its doc text (`title` /
  `markdownDescription`), no type, no nested schema, no `$ref` (which would dangle outside the
  bundle pool). That is enough for the editor to complete both the `type` value *and* the keys
  under it (e.g. `apiToken`, `monitorId`), and the carried-over `required` list prompts mandatory
  keys exactly like an installed plugin. Value-level completion and real validation still need the
  install.
- "every occurrence" is load-bearing (that was the second bug): the generator does **not** route
  every subtype list through the discriminator base-class definition. The `flow` schema inlines the
  full installed-subtype `anyOf` directly at each property site (`Flow.tasks.items`, `errors`, every
  flowable task's nested `tasks`, … — 15 sites on a real schema) while the `Task` definition itself
  is barely referenced. Patching only the named definition therefore never reached what the editor
  actually completes from. The merge instead extends **every `anyOf` array whose `$ref` keys
  intersect a bundle discriminator's subtype set** — a safe heuristic because the generator always
  emits the *full* registered subtype list at such sites and the subtype sets of distinct
  discriminators (task/trigger/…) are disjoint. The named discriminator definition is still patched
  explicitly, covering the empty-`anyOf` case the intersection can't see.
- this **mirrors the exact shape of an installed subtype** (a `$ref` to an object definition), only
  without the plugin's full property schema. The structural parity is load-bearing: the editor's YAML
  language service offers a `type` const from an `anyOf` branch that resolves to an *object*
  definition — an inline, type-less stub is silently skipped (that was the first bug: the type never
  autocompleted). Omitting the heavy property schema keeps the response small — shipping the full
  property schema of every catalog plugin (thousands of types) would balloon it past what the browser
  worker can process. Property-level completion for a plugin arrives once it is installed and its full
  definition enters the local schema.

Dedup is by FQCN, so an installed subtype is never shadowed and re-merging is idempotent. **Installed
plugins take precedence** over the bundle. Draft-7 shape (`definitions`, not `$defs`).

> **Size matters.** Copying the whole catalog's *full* definitions produced a ~12 MB schema per flow
> file that silently broke completion in monaco-yaml. Lightweight definitions keep the bundle's
> contribution to a few MB: each stub definition appears once (property-name shells + doc text
> included), only the ~50-byte `$ref` branches repeat per site — e.g. a heavy full-plugin dev
> install goes 6.9 → 9.8 MB merged. If that ever creeps toward the monaco ceiling, the per-property
> `markdownDescription` copy is the first thing to drop (~1.5 MB). On a slim (`-slim`)
> install — the actual target — the local schema is small too, so the merged result stays light.

Merge-by-FQCN works only because both sides come from the same `JsonSchemaGenerator.schemas()` — the
runtime local schema and the bundle — so a class always resolves to the same definition key and the
same discriminator layout. To keep that alignment (and shrink the bundle), `schemas()` collapses each
single-use `<Class>` + `<Class>-2` discriminator-wrapper pair into one definition, applied identically
on both sides.

The merged response is cached only **60s** (`CATALOG_CACHE_DIRECTIVE`), not the usual hour — a
newly-installed plugin must show up in the editor promptly, not up to an hour later.

### (d) Frontend

`ui/src/override/utils/yamlSchemas.ts` points `monaco-yaml` at those endpoints with
`includeCatalog=true` (flow, task, trigger, plugindefault — dashboard stays installed-only), so the
editor receives a schema covering the whole catalog and offers completion/validation for plugins
that were never installed.

---

## Feature 2 — Auto-download / install on save ("Save & Fetch")

When a flow referencing an uninstalled plugin is saved, `PluginAutoInstallService`:

1. Parses the flow YAML and collects every package-shaped `type` value (recursive walk,
   `collectTypes`; non-FQCN values like input `type: STRING` or retry `type: constant` are ignored).
2. Keeps those absent from the `PluginRegistry` — `findMissingTypes`.
3. Maps each missing FQCN to a Maven artifact via `PluginCatalogService`, using **longest-prefix
   match** on the plugin's package group (so `io.kestra.plugin.scripts.python` beats
   `io.kestra.plugin.scripts`) — `findArtifactForType`.
4. Downloads & installs via `PluginManager` (works with both `LocalPluginManager` — OSS, local disk —
   and `RemotePluginManager` — EE, cluster-wide).
5. **Reloads in-process, no container restart** — `install(..., refreshPluginRegistry=true)`
   refreshes the `PluginRegistry` so the new classes resolve immediately, then `JsonSchemaCache` is
   cleared so the new types show up in the served schema. Without this the experience would regress
   to "restart your container after save".

### Async job + live progress

- `POST /api/v1/plugins/install` enqueues the install and returns a **job id** immediately (HTTP
  202). Only artifacts the plugin catalog maps are accepted (**allowlist** — an arbitrary Maven
  coordinate is rejected with 400; the feature flag off yields 403). `PluginInstallJobRegistry` runs
  the job with in-flight **dedup** (a resubmitted identical artifact set joins the running job), a
  cap on concurrent active jobs (429 beyond it) and a hard per-job timeout that cancels a stalled
  resolve. `PluginInstallTransferListener` reports byte-level download progress. Poll
  `GET /api/v1/plugins/install/{jobId}` for status (jobs kept ~1h after completion).
- `POST /api/v1/plugins/auto-install/detect` parses a flow YAML and returns the missing types +
  resolved artifacts (empty result — not an error — when disabled or all types are known).
- The frontend shows `PluginInstallToast.vue` with live progress and **blocks the flow save until the
  install job completes** (`useFlowEditorActions.ts`; `Ctrl+S` goes through the same path as the Save
  button).

### Feature gating

On by default **only for OSS + local-filesystem storage** (`edition == OSS && storage.type == local`),
off everywhere else — a standalone deployment on S3/GCS stays inert. Setting
`kestra.plugins.auto-install.enabled` explicitly (`true`/`false`) always wins over that computed
default, and `server local` sets it to `true` explicitly. Resolved once in
`PluginAutoInstallService` from `EditionProvider` and the `kestra.storage.type` property.

There is deliberately **no per-user permission gating** on the install endpoints beyond the
instance's authentication: enabling the flag *is* the admin's governance decision — self-service
plugin acquisition for every authenticated user — and the catalog allowlist bounds what can be
installed to official plugins, so the residual risk is resource usage, capped by the install-job
registry's active-job limit.

---

## Key files

| Area | File |
|------|------|
| Bake bundle (CLI) | `cli/.../schema/PluginsSchemaCommand.java` |
| Fetch/cache/merge bundle | `core/.../plugins/PluginSchemaBundleService.java` |
| Schema generation | `core/.../docs/JsonSchemaGenerator.java` |
| Serve schema (`includeCatalog`) | `webserver/.../api/PluginController.java` |
| Auto-install logic | `core/.../plugins/PluginAutoInstallService.java` |
| Catalog (FQCN → artifact) | `core/.../plugins/PluginCatalogService.java` |
| Async install job | `core/.../plugins/PluginInstallJob{,Registry}.java`, `PluginInstallTransferListener.java` |
| Detect result DTO | `core/.../plugins/PluginAutoInstallDetectResult.java` |
| Editor schema wiring | `ui/src/override/utils/yamlSchemas.ts` |
| Install UI + save gating | `ui/src/components/plugins/PluginInstallToast.vue`, `ui/src/components/flows/useFlowEditorActions.ts` |
| CI publish step | `.github/workflows/main-build.yml`, `pre-release.yml` |
| Bundle embedding in the JAR | `cli/build.gradle` (`-PpluginsSchemaBundle`) |

## Configuration

| Property | Default | Effect |
|----------|---------|--------|
| `kestra.plugins.schema-bundle-path` | unset | Explicit local-file bundle, highest priority — wins over the JAR-embedded resource and the URL template. Used by `plugin-devtools` to inject a full-catalog dev bundle. |
| `kestra.plugins.schema-bundle-url-template` | empty | URL of a self-hosted bundle (`{version}` placeholder, resolved to the stripped stable version, e.g. `1.2.3`). Lowest priority, and empty by default because the bundle ships in the jar — set it only for a custom build that has no embedded bundle. |
| `kestra.plugins.auto-install.enabled` | unset → `true` on OSS+local storage, else `false` | Auto-download missing plugins on save. Unset → computed default (`edition == OSS && storage.type == local`); an explicit value always wins. `server local` sets it to `true` explicitly. |
| `kestra.plugins.auto-install.install-timeout` | `PT2M` | Bounded wait for the boot-time and first-sync-migration installs. |
| `kestra.plugins.auto-install.save-timeout` | `PT30S` | Bounded wait for the synchronous save-path install hook — shorter so a bulk import never serializes minutes per flow behind the install pool. |

> **No instance phones home for the bundle.** It is read from the jar's own classpath, so an air-gapped or offline deployment gets catalog completion with no egress and nothing to configure. Both remaining sources are opt-in and empty by default: an explicit local file (`schema-bundle-path`) or a self-hosted URL (`schema-bundle-url-template`). Downloading the plugin **JARs** on save is a separate, gated concern — see `kestra.plugins.auto-install.enabled` and the table below.

## Who downloads what

| Actor | Downloads | Size |
|-------|-----------|------|
| CI (once per release) | all plugin JARs → emits `plugins-schema.json` | several GB, ephemeral |
| Kestra instance (runtime) | nothing — `plugins-schema.json` is read from the jar | a few MB of JSON, already on disk |
| Browser (editor) | merged schema via `?includeCatalog=true` | a few MB of JSON |
| Kestra instance (on save) | JARs of the **missing referenced** plugins only | as needed |
