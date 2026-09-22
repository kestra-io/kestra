# @kestra-io/design-system

Kestra's design system: `Ks*` component abstractions over [Element Plus](https://element-plus.org)
(namespaced `kel`), the `--ks-*` design tokens, and the shared date / duration / filter utilities.

It is the **single source of truth for every design decision** in Kestra — colors, typography,
spacing, radii, shadows and the component vocabulary. The rules for using it, the component
catalogue and the token reference live in [`ui/AGENTS.md`](../../AGENTS.md); read that before
writing feature code against this package.

## Peer dependencies

The package ships no framework of its own: `vue`, `element-plus`, `vue-i18n`, `vue-router`,
`vue-material-design-icons`, `moment`, `moment-timezone`, `echarts`, `monaco-editor`, `bootstrap`
and `yaml` are peer dependencies, so the host app owns their versions. See `peerDependencies` in
[`package.json`](package.json) for the supported ranges.

## Using it

### Global install (the plugin)

The default export is a Vue plugin. It registers every `Ks*` component globally, wires the
Element Plus services that need an app context (`ElMessage`, `ElMessageBox`, `ElNotification`,
`ElLoading`, `ElInfiniteScroll`, the popover directive), sets the `kel` namespace, registers the
`v-ks-loading` directive, and merges the design system's own i18n messages into the app's
`vue-i18n` instance:

```ts
import {createApp} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"

const app = createApp(App)
app.use(i18n)                 // install vue-i18n first so DS locales can be merged
app.use(KestraDesignSystem)
```

This is what the Kestra UI does ([`ui/src/utils/init.ts`](../../src/utils/init.ts)), and it is why
templates can write `<KsButton>` without importing it. It is the least tree-shakeable option: the
barrel pulls in every component. The chart components (`KsEchart`, `KsLine`, `KsBar`, `KsPie`,
`KsGraph`) are deliberately registered as async components so ECharts stays out of the eager bundle.

If you install the plugin **before** `vue-i18n`, the DS messages are not merged; register them
yourself in that case:

```ts
import {registerDesignSystemI18n, setDesignSystemLocale} from "@kestra-io/design-system"

await registerDesignSystemI18n(i18n)
setDesignSystemLocale("fr")
```

### Named imports from the barrel

Components, composables and utilities are all named exports, so a component can be imported
explicitly instead of relying on global registration:

```ts
import {KsButton, KsDataTable, cssVar, durationUtils, useTheme} from "@kestra-io/design-system"
```

Use this for utilities and composables, and for components in a host that does not install the
plugin. Note that the package declares no `sideEffects: false` (component CSS is emitted per
chunk), so bundlers keep the barrel's module graph: importing one component from `"."` still loads
the others. Reach for the per-component entry below when bundle size is what matters.

### Per-component imports (tree shaking)

The published package exposes one entry per component — the build generates an `exports` map with
a subpath for every `.vue` file under `src/components`, each with its own JS and CSS chunk:

```ts
import KsButton from "@kestra-io/design-system/components/Basic/KsButton/KsButton"
import KsDataTable from "@kestra-io/design-system/components/Data/KsDataTable/KsDataTable"
```

Only that component and its dependencies end up in the bundle. Those extensionless subpaths are
the published shape: inside this monorepo the exports map points at `src`, so the workspace app
compiles the sources directly and gets HMR, and a deep import there keeps the extension —
`@kestra-io/design-system/components/Basic/KsButton/KsButton.vue`.

### `import *`

Avoid it:

```ts
import * as ds from "@kestra-io/design-system"   // don't
```

A namespace import materializes the whole barrel, and any dynamic member access (`ds[name]`)
makes the graph unanalyzable, so nothing can be dropped.
Import the names you use, or the per-component entry. The one legitimate use is a test that needs
to stub or enumerate the exports.

### Styles and tokens

The global stylesheet (Element Plus theme, Bootstrap reset, fonts, `--ks-*` tokens for light and
both dark themes) is a separate entry of the published package, imported once at bootstrap:

```ts
import "@kestra-io/design-system/styleBase"
```

In-repo consumers pull the SCSS source instead — this is what
[`ui/src/styles/app.scss`](../../src/styles/app.scss) does:

```scss
@use "@kestra-io/design-system/src/assets/styles/variables.scss" as design;
@use "@kestra-io/design-system/src/assets/styles/index.scss" as *;
```

In feature code, read colors through `var(--ks-*)` in CSS, or `cssVar("--ks-status-success")` when
a value is needed in JS (chart configs). Never hardcode a hex, an `--el-*` or a raw pixel value.

### Task icons

`KsEditor` (Monaco suggestions) and the topology package render plugin icons, which depend on the
host's API. The app provides its own component once, at bootstrap:

```ts
import {TASK_ICON_INJECTION_KEY} from "@kestra-io/design-system"

app.provide(TASK_ICON_INJECTION_KEY, TaskIcon)
```

Without it, `useTaskIcon()` falls back to a generic placeholder icon.

## Developing the package

```bash
npm install                 # from ui/, installs the workspace
npm run storybook           # component workbench on :6007
npm run play                # standalone vite playground
npm run build               # tsdown: JS + per-component .d.ts + CSS into dist/
npm run dev                 # same, in watch mode

npm run test                # lint + types + unit + storybook, in parallel
npm run unit:test           # vitest units only
npm run storybook:test      # storybook component tests (needs chromium)
npm run types:test          # vue-tsc --noEmit
npm run lint:fix            # oxlint + eslint, with fixes
```

Every new `Ks*` component needs a Storybook story; prefer a story over a Vitest unit test when the
behavior is rendering behavior.

Two things to know about `npm run build`:

- every `.vue` file under `src/components` becomes a build entry, so a component that **no other
  module imports** fails declaration emit (`Unable to load file … from the program`). Delete dead
  components rather than leaving them unreferenced.
- with `CI=true` the build rewrites the `exports` map in `package.json` to point at `dist`. That is
  expected in CI and reverted there; don't commit it.

## Publishing a version

Publishing is a manual GitHub Actions run — there is no release on merge, and the version in git
stays `0.0.0-dev` (the real number is derived from npm at publish time).

1. Open [**Actions → Publish Design System**](https://github.com/kestra-io/kestra/actions/workflows/publish-design-system.yml)
   in `kestra-io/kestra`.
2. Click **Run workflow** and fill in the inputs:

   | Input | Value |
   |---|---|
   | *Use workflow from* | the branch to publish from, normally `develop` |
   | `increment` | `patch`, `minor` or `major` |
   | `package` | `design-system` (the same workflow publishes `topology`) |
   | `skip-test` | `false`; only `true` when the tests were already green on that exact commit |

3. Click **Run workflow** and watch the run.

The workflow then: runs the package's tests, builds it, reads the latest version published on
npmjs.com, applies the increment to it, tags the commit `design-system/v<version>` and pushes the
tag, then publishes to npm with trusted publishing (hence `id-token: write` and the npm upgrade
step — trusted publishing needs npm 11+).

The CLI equivalent, if you prefer it:

```bash
gh workflow run publish-design-system.yml --ref develop \
  -f package=design-system -f increment=patch -f skip-test=false
```

Afterwards, bump `@kestra-io/design-system` in whichever consumers should pick the version up
(`ui/package.json`, `ui-ee/package.json`, `@kestra-io/topology`).

**If the run fails:** the *Build* step is the one that fails on a broken package rather than a
broken test — most often on declaration emit for an unreferenced component (see above). Test
failures are the package's own suite and are reproducible with `npm run test`. A failure before the
*Publish to npm* step leaves nothing published, but a failure **after** the *Commit and tag* step
leaves the tag pushed: delete it (`git push origin :refs/tags/design-system/v<version>`) before
re-running, or the next run will collide.
