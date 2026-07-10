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

The target is the **OSS user evaluating Kestra on a standalone deployment** — spinning
Kestra up locally or on a single VM. The goal is zero friction between "just started Kestra" and
"first flow runs": no need to know which plugins exist, edit a Dockerfile, or restart anything. The
local plugins directory is the storage target.

Deliberately **out of scope**, which is why auto-download ships **disabled by default** and is meant
to be gated off outside standalone:

- **OSS distributed / production** — wants predictable, pre-provisioned plugin sets; on-demand fetch
  at save time is undesirable there.
- **Enterprise Edition** — plugin installation is a governed, cluster-level admin operation;
  auto-fetch on save would bypass that governance.

### Distribution: two images

The feature assumes a dual-image distribution (the build itself lives in the `actions` repo, out of
scope for this codebase):

- `kestra/kestra:<version>` — **full** image, all plugins bundled (offline / air-gapped use).
- `kestra/kestra:<version>-no-plugins` — **lean** image, core engine only. This is the image that
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

The output, `plugins-schema.json`, is a few MB of JSON. It is uploaded to GCS at
`configuration-schema/{version}/` (a new CI step in `main-build.yml` / `pre-release.yml`, reusing
the existing schema-publishing composite action).

> **Not `oss.json`.** `oss.json` (from `configuration-schema`) describes the *instance
> configuration* (`application.yml` settings + storages). `plugins-schema.json` describes the
> *flow/task/trigger* vocabulary. Different generator, different content, different consumer — a
> separate file on purpose. They share only the CI transport (same job, bucket, versioned folder).

### (b) Runtime — fetch & cache

`PluginSchemaBundleService` downloads the bundle from a configurable URL,
`kestra.plugins.schema-bundle-url-template` (`{version}` → current stable version). Cached ~1h,
loaded asynchronously, and a **no-op when the property is empty** (the default). This is pure JSON —
**no plugin JAR is ever downloaded here.**

### (c) Serve — merge on the fly

`GET /api/v1/plugins/schemas/{type}?includeCatalog=true` (`PluginController`) starts from the
**local** schema (installed plugins) and enriches it with the bundle via
`PluginSchemaBundleService.mergeWithBundle`:

- copies bundle `definitions` missing locally, keyed by FQCN (the stable key a class always resolves
  to, from either registry) — `mergeDefinitions`;
- extends the `anyOf` of **every** polymorphic discriminator present in the local schema — the
  requested root *and* any embedded one (e.g. the `Task` discriminator nested in the `flow` schema's
  `tasks`) — with bundle branches missing locally — `mergeDiscriminatorAnyOf`.

Idempotent: re-merging never duplicates a definition or a branch. **Installed plugins take
precedence** over the bundle. Draft-7 shape (`definitions`, not `$defs`).

Merge-by-FQCN works only because both sides come from the same `JsonSchemaGenerator.schemas()` — the
runtime local schema and the bundle — so a class always resolves to the same definition key and the
same discriminator layout. To keep that alignment (and shrink the bundle), `schemas()` collapses each
single-use `<Class>` + `<Class>-2` discriminator-wrapper pair into one definition, applied identically
on both sides.

The merged response is cached only **60s** (`CATALOG_CACHE_DIRECTIVE`), not the usual hour — a
newly-installed plugin must show up in the editor promptly, not up to an hour later.

### (d) Frontend

`ui/src/override/utils/yamlSchemas.ts` points `monaco-yaml` at those endpoints with
`includeCatalog=true`, so the editor receives a schema covering the whole catalog and offers
completion/validation for plugins that were never installed.

---

## Feature 2 — Auto-download / install on save ("Save & Fetch")

When a flow referencing an uninstalled plugin is saved, `PluginAutoInstallService`:

1. Parses the flow YAML and collects every `type` FQCN (recursive walk, `collectTypes`).
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
  202). `PluginInstallJobRegistry` runs the job; `PluginInstallTransferListener` reports byte-level
  download progress. Poll `GET /api/v1/plugins/install/{jobId}` for status (jobs kept ~1h after
  completion).
- `POST /api/v1/plugins/auto-install/detect` parses a flow YAML and returns the missing types +
  resolved artifacts (empty result — not an error — when disabled or all types are known).
- The frontend shows `PluginInstallToast.vue` with live progress and **blocks the flow save until the
  install job completes** (`useFlowEditorActions.ts`; `Ctrl+S` goes through the same path as the Save
  button).

### Feature gating

Disabled by default: `kestra.plugins.auto-install.enabled=true` to enable.

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

## Configuration

| Property | Default | Effect |
|----------|---------|--------|
| `kestra.plugins.schema-bundle-url-template` | empty | Bundle URL (`{version}` placeholder). Empty → bundle disabled, completion falls back to installed-only. |
| `kestra.plugins.auto-install.enabled` | `false` | Enable auto-download of missing plugins on save. |

## Who downloads what

| Actor | Downloads | Size |
|-------|-----------|------|
| CI (once per release) | all plugin JARs → emits `plugins-schema.json` | several GB, ephemeral |
| Kestra instance (runtime) | `plugins-schema.json` from GCS | a few MB of JSON |
| Browser (editor) | merged schema via `?includeCatalog=true` | a few MB of JSON |
| Kestra instance (on save) | JARs of the **missing referenced** plugins only | as needed |
