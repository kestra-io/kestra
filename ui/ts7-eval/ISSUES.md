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
```

## 1. microsoft/typescript-go: TS5088 regression in 7.1 (new)

No existing issue was found for `TS5088` or "cyclic structure" in microsoft/typescript-go.
Dependency-free repro: `repros/ts5088-min/`; the Vue shape that found it: `repros/ts5088/`.

> **Title:** 7.1 regression: TS5088 on an inferred return type through a recursive mapped type (7.0 emitted it)
>
> With `declaration: true`, an exported function whose inferred return type maps a self-referencing type through a
> recursive mapped type now fails with TS5088. 7.0.2 and 6.0.3 compile the same code and emit the cycle as
> `/*elided*/ any`.
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
> | Version | Result |
> |---|---|
> | 6.0.3 | OK, emits `child?: /*elided*/ any \| undefined` |
> | 7.0.2 | OK, same output |
> | 7.1.0-beta | `index.ts(6,17): error TS5088: The inferred type of 'make' references a type with a cyclic structure which cannot be trivially serialized. A type annotation is necessary.` |
> | 7.1.0-dev.20261106.1 | same as 7.1.0-beta |
>
> **Expected:** the 7.0 behaviour, or an entry in the 7.1 breaking-changes notes if the error is now intended.
>
> **Why it matters:** this is the shape of Vue's `ref<T>()` (`UnwrapRef` is a recursive mapped type) on any recursive
> data type, such as a JSON Schema. In a `composite` project the error also suppresses that file's declaration, so
> every downstream project reports TS6305 / TS7016 against it: 6 of these produced 136 follow-up errors in our UI.
> Annotating the return type works around it.

## 2. vuejs/language-tools#6334: comment draft (known)

> Same failure at scale: 262 of 1,095 diagnostics in the Kestra UI (Vue 3.5), and 166 more in its EE layer, are `onX` listeners (`@click`,
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
