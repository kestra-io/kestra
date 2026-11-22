# Upstream reports

Everything below reproduces with the files in `repros/` and these versions: `typescript@7.1.0-beta`,
`@vue/content-mapper@4.0.0-alpha.3` (with `@vue/language-core@4.0.0-alpha.3`), `tsrs@0.22.0-ts7.1.0-beta`, `vue@3.5.44`.
tsgo and tsrs report the same diagnostics for every repro, and vue-tsc 3.3.14 accepts all of them.

```bash
cd ui/ts7-eval && npm ci
node node_modules/typescript7/bin/tsc -p repros --runExternalCode
node_modules/.bin/tsrs -p repros --runExternalCode
node node_modules/typescript7/bin/tsc -p repros/ts5088
node node_modules/typescript7/bin/tsc -p repros/ts5088-min
bash repros/ts2883-min/repro.sh node_modules/typescript7/bin/tsc
```

## Status, checked 2026-11-20

TypeScript 7 reports live in microsoft/TypeScript, not microsoft/typescript-go. `@vue/content-mapper@4.0.0-alpha.3`
(2026-11-01) is still the newest alpha, and npm has no 7.1 RC yet (`beta` is `7.1.0-beta`, `latest` is `7.0.2`).

| Item | Upstream | State | Still reproduces |
|---|---|---|---|
| TS5088 | microsoft/TypeScript#64656 | Closed as fixed on 2026-11-18 by microsoft/TypeScript#65567, milestone 7.1.1 RC | No: `repros/ts5088-min` passes on `7.1.0-dev.20261120.1` |
| TS2883 | microsoft/TypeScript#65601 | Filed 2026-11-20 with https://github.com/elevatebart/repro-with-package-installed-twice | Yes, on `7.1.0-dev.20261120.1`. Last good `20261012.1`, first bad `20261013.1` |
| Native listener | vuejs/language-tools#6334 | Open, labelled "pending triage", no maintainer comment, no PR | Yes. vue-tsc 3.3.14 reports the same once `checkUnknownProps`, `checkUnknownEvents` and `checkUnknownComponents` are all on |
| `aria-*` / `role` | vuejs/language-tools#6318 | Open, no maintainer comment; fix proposed in vuejs/language-tools#6323 (open, not reviewed) | Yes. vue-tsc 3.3.14 reports the same with `checkUnknownProps` alone |
| `v-else-if` narrowing | vuejs/language-tools#6306 | Open, duplicate vuejs/language-tools#6356; fixes proposed in vuejs/language-tools#6359 and #6330 (not reviewed) and #6317 (changes requested by a member) | Yes, v4 only |

Sections 2 and 3 describe vue-tsc 3's strict template checks made mandatory in v4 rather than mapper bugs; only
section 4 is v4-only.

## 1. microsoft/TypeScript#65601: TS2883 on a package installed twice (filed)

Two identical copies of a package (same name and version) in two `node_modules` folders, a project that compiles a
file from the other folder, and a type that the package only exposes through a subpath export: 7.1 deduplicates the
copies, then finds no specifier for the kept one from the other folder. 6.0.3 and 7.0.2 emit
`import("pkg/utils/arrayable")`. Copies at different versions do not trigger it.
`repros/ts2883-min/repro.sh` is the element-plus-shaped version of the standalone repro.

Workarounds, tested on 7.0 and 7.1: a `paths` entry mapping the package to one copy, installing it once (symlinked or
in a shared parent folder), or annotating the return type. kestra-io/kestra-ee#12729 removed all 12 EE TS2883: it maps
`@popperjs/core` to the OSS copy (2), and type-checks the OSS project against the EE SDK through
`ui-ee/tsconfig.oss.json`, as Vite already serves it (10). The OSS and EE SDKs are different packages that share the
name `@kestra-io/kestra-sdk@2.0.0`, so the store types EE re-exported came from a package EE could not name.

TS5088, the other half of the original draft, is closed as fixed by microsoft/TypeScript#65567, and
`repros/ts5088-min` passes on `7.1.0-dev.20261120.1`. Whether the app still needs the `Ref<T>` annotations of
kestra-io/kestra#22389 on that nightly is untested.

## 2. vuejs/language-tools#6334: comment draft (known)

> Same failure at scale: 262 of 800 diagnostics in the Kestra UI (Vue 3.5), and 166 more in its EE layer, are `onX` listeners (`@click`,
> `@keydown`, and component events forwarded through `$attrs` such as `@closed` / `@visible-change`) on components that
> declare no matching emit. Minimal repro: `repros/NativeListener.vue` (`<Child @click="() => {}" />` gives TS2353 on
> `onClick`). The `emits: ["click"]` workaround does not apply to wrappers that forward a third-party component's events.

## 3. vuejs/language-tools#6318: comment draft (known)

> Another data point: 151 `aria-*` / `role` diagnostics in the Kestra UI (55 more in its EE layer), mostly on design-system wrappers. `htmlAttributes` defaults to
> `['aria-*']` yet the attribute is still rejected on a component, whatever its root is: `repros/AriaOnComponent.vue`
> errors both on `<Child>` (root `<button>`) and on `<Wrapper>` (root is another component). Native elements are fine.

## 4. vuejs/language-tools#6306: comment draft (known)

> Reproduces with a `<template v-else-if>` that wraps a `v-for` component and a native `@click.stop` inside one of its
> slots, which is the shape of our 12 occurrences (`repros/NarrowingInSlotVFor.vue`):
> `error TS2367: This comparison appears to be unintentional because the types '"files"' and '"flows"' have no overlap.`

## Not reported

- vuejs/language-tools#6315 (TS7027 on inline handlers) does not show up here: `allowUnreachableCode` is unset, so the
  diagnostic is a suggestion and never reaches the CLI output.
- `data-*` attributes are rejected by default in v4 (`dataAttributes: []`). That is configuration rather than a bug,
  so the eval sets `"dataAttributes": ["data-*"]`, which removes 517 diagnostics.
- tsrs vs tsgo: 4 EE messages once listed an inferred type's properties in a different order (same file, line and
  code). That run had stray `.d.vue.ts` files in `ui-ee/src`; the clean rerun is identical, and
  `-p --checkerAssignment go --noLazyMembers` matched tsgo even before. Not a tsrs bug.
- TypeScript 7 defaults `rootDir` to the tsconfig directory. `ui-ee/tsconfig.app.json` sets none and includes
  `../../kestra/ui` files, so it fails with TS6059 until `rootDir` is set: a migration item, not a bug.
