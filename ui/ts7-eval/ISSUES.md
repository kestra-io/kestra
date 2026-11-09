# Draft upstream reports (not filed)

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

## 1. microsoft/typescript-go: two declaration-emit regressions in 7.1 (new)

No open issue matches `TS5088`, `TS2883`, "cyclic structure" or "likely not portable" in microsoft/typescript-go.
Closed and possibly related: microsoft/typescript-go#3596 (a TS2883 regression with project references, closed
2026-04-24) and microsoft/typescript-go#4147 (`-b` emitting a different subpath in declarations, closed 2026-06-01).
Both compile on 6.0.3 and 7.0.2 and fail on 7.1.0-beta and 7.1.0-dev.20261106.1, both are declaration-emit errors on
an inferred return type, and annotating the return type works around both. They may share a cause; split them into two
issues if the maintainers prefer. Repros: `repros/ts5088-min/` and `repros/ts2883-min/repro.sh`.

> **Title:** 7.1 regressions in declaration emit: TS5088 on recursive mapped types, TS2883 on deduplicated packages
>
> Two inferred return types that 7.0.2 (and 6.0.3) emit fine fail in 7.1.0-beta and in `7.1.0-dev.20261106.1`, with
> `declaration: true`.
>
> ### TS5088 on a recursive mapped type
>
> ```ts
> type Deep<T> = T extends object ? { [K in keyof T]: Deep<T[K]> } : T;
> declare function wrap<T>(value: T): { value: Deep<T> };
>
> export type Node = { child?: Node };
>
> export function make() {
>     return wrap<Node>({});
> }
> ```
>
> `tsconfig.json`: `{"compilerOptions": {"strict": true, "declaration": true, "emitDeclarationOnly": true, "target": "es2022", "module": "esnext", "moduleResolution": "bundler"}}`
>
> - 6.0.3, 7.0.2: OK, the cycle is emitted as `child?: /*elided*/ any | undefined`.
> - 7.1: `index.ts(6,17): error TS5088: The inferred type of 'make' references a type with a cyclic structure which cannot be trivially serialized. A type annotation is necessary.`
>
> This is the shape of Vue's `ref<T>()` (`UnwrapRef` is a recursive mapped type) on any recursive data type.
>
> ### TS2883 on a subpath of a deduplicated package
>
> Two identical copies of a package (same name and version) are in the program: `app/node_modules/pkg` and
> `lib/node_modules/pkg`. `pkg` only exposes `Arrayable` through a wildcard subpath export
> (`"./es/*.mjs": {"types": "./es/*.d.ts"}`), as element-plus does. `app` compiles a file of `lib`:
>
> ```ts
> // app/src/types.ts, listed first so that app's copy of pkg is the one kept
> export type { Rules } from "pkg";
>
> // lib/src/rules.ts
> import type { Rules } from "pkg"; // Rules = Record<string, Arrayable<Rule>>
>
> export function useRules(rules: Rules) {
>     return { first: Object.values(rules)[0] };
> }
> ```
>
> A script that builds the whole layout in a temp dir is attached (`repro.sh <path to tsc>`).
>
> - 6.0.3, 7.0.2: OK, `lib/src/rules.d.ts` references `import("pkg/es/utils/typescript.mjs").Arrayable`.
> - 7.1: `lib/src/rules.ts(3,17): error TS2883: The inferred type of 'useRules' cannot be named without a reference to 'Arrayable' from '../../app/node_modules/pkg/es/utils/typescript'. This is likely not portable. A type annotation is necessary.`
>
> 7.1 resolves the deduplicated file to app's copy and then finds no specifier for it from `lib`, where 7.0.2 used
> `pkg/es/utils/typescript.mjs`, which `lib` can resolve. If `pkg` re-exports `Arrayable` from its root, 7.1 emits
> `import("pkg")` and passes, so the subpath export is part of the trigger. Two checkouts sharing a UI library
> (here an app and its enterprise layer, with the same element-plus, vue-router and SDK versions) hit this layout.
>
> Possibly related, both closed before 7.0.2 shipped: #3596 and #4147. This layout passes on 7.0.2, so it is either
> a new path or a reintroduction.
>
> **Expected:** the 7.0 behaviour for both, or entries in the 7.1 breaking-changes notes if either error is intended.
>
> **Why it matters:** in a `composite` project both errors also suppress the file's declaration, so every downstream
> project reports TS6305 / TS7016 against it. In our UI, before we annotated the affected exports, 6 TS5088 led to
> 136 follow-up errors in one build, and 12 TS2883 (with the same TS5088) led to 69 in another.

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
