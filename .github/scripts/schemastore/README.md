# SchemaStore release updates

`update-schemastore.yml` runs after a stable GitHub release is published. It verifies
the released executable's SHA-256 checksum and extracts its embedded
`plugins-schema.json`. It follows the flow root's references to produce a standalone
Draft 7 schema, retaining full plugin definitions without starting a server or
consulting the live plugin catalog. Releases without a full bundle fail explicitly.

SchemaStore uses full versions (`kestra-MAJOR.MINOR.PATCH.json`). The script preserves
existing versions and only advances the catalog default for a newer version. It
copies the existing Kestra validation settings, registers Kestra's custom metadata
keywords and `partial-time` format, adds an example flow, and runs SchemaStore's
formatter and `cli.js check` before preparing a review patch. SchemaStore's other
PR checks, including spelling, still run upstream; new validation incompatibilities
fail the workflow before it publishes a PR.

## Repository setup

- Set repository variable `SCHEMASTORE_FORK` to a dedicated, existing fork of
  `SchemaStore/schemastore`, in `owner/repository` format.
- Set secret `SCHEMASTORE_TOKEN` to a bot token that can push branches to that fork
  and open pull requests against the public upstream repository. A classic PAT
  with `public_repo` is one option for a public fork; use the organization's
  preferred credential policy. The ordinary Kestra `GITHUB_TOKEN` is not sufficient.
- Branches named `chore/kestra-MAJOR.MINOR.PATCH` in that fork are owned by this
  automation. A rerun replaces that branch using a force-with-lease and reuses its
  open PR. Changes already present upstream produce no PR.

Publishing remains disabled during manual runs unless `dry-run` is explicitly
unchecked. Manual dry runs are also supported in forks of Kestra; publishing runs
only in `kestra-io/kestra`. Dry runs need neither the SchemaStore fork nor its token
and upload a validated `schemastore.patch` artifact. SchemaStore maintainers still
review and merge PRs.

## Local validation

In a separate SchemaStore checkout, run `npm ci --ignore-scripts`, then from Kestra:

```sh
export SCHEMASTORE_DIR=/absolute/path/to/schemastore
node --test .github/scripts/schemastore/update.test.mjs
node .github/scripts/schemastore/update.mjs "$SCHEMASTORE_DIR" /path/to/plugins-schema.json v2.0.5
```

Inside the SchemaStore checkout, run:

```sh
npx --no-install prettier --write src/schemas/json/kestra-2.0.5.json src/test/kestra-2.0.5/hello-world.yaml src/api/json/catalog.json src/schema-validation.jsonc
node cli.js check --schema-name=kestra-2.0.5.json
```

Only use a bundle extracted from the corresponding release when preparing an
actual update. Tests use synthetic bundles to cover version ordering, missing
definitions, comments in JSONC, maintenance releases, and repeat runs.
