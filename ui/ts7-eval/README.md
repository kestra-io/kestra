# TypeScript 7.1 + `@vue/content-mapper` evaluation

An experiment, not a toolchain change: `npm run check:types` (vue-tsc 3 on TypeScript 6) remains the source of truth,
and nothing here is a required CI check or part of the editor setup.

This folder is a separate npm package with its own lockfile, deliberately kept outside the `ui` workspaces.
`@vue/content-mapper` has a peer dependency on `typescript >=7.1.0-beta` and loads `typescript/sync` from the
project directory, while vue-tsc 3, `@vue/typescript-plugin` and typescript-eslint need the TypeScript 6 API.
npm `overrides` do not apply to peer dependencies, so installing all of them in `ui/` fails with ERESOLVE unless
`--legacy-peer-deps` is passed, which would then be needed for every install.

| File | Purpose |
|---|---|
| `tsconfig.mapper.json` | `contentMappers` entry and redirected outputs, mixed into every config below |
| `tsconfig.ds.json`, `tsconfig.ts7.json`, `tsconfig.test.json` | The three projects of `check:types`, extended from `ui/` so TypeScript 6 tooling never reads `contentMappers` |
| `tsconfig.flat.json` | One `-p` project without references, for tsrs flags that `--build` rejects (`--maxMemory`, `--checkerAssignment`) |
| `scripts/bench.sh` | Cold runs (hyperfine, GNU `time -v`, process-tree RSS sampler), then `report.mjs` |
| `scripts/triage.mjs` | Buckets the diagnostics; every bucket is backed by a file in `repros/` |
| `results/` | Tables, per-run diagnostics and triage from the last benchmark |
| `ISSUES.md` | Upstream report drafts, not filed |

```bash
cd ui
npm run typecheck:ts7      # tsgo, --build over the three projects
npm run typecheck:tsrs     # tsrs, same graph
cd ts7-eval && RUNS=5 bash scripts/bench.sh && node scripts/triage.mjs tsgo
```

tsrs ships no Windows arm64 build, and `bench.sh` needs Linux (`ps`, GNU `time`); on macOS arm64 use `time -l`.
