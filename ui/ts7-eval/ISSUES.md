# Draft upstream reports (not filed)

Everything below reproduces with the files in `repros/` and these versions: `typescript@7.1.0-beta`,
`@vue/content-mapper@4.0.0-alpha.3` (with `@vue/language-core@4.0.0-alpha.3`), `tsrs@0.22.0-ts7.1.0-beta`, `vue@3.5.44`.
tsgo and tsrs report the same diagnostics for every repro, and vue-tsc 3.3.14 accepts all of them.

```bash
cd ui/ts7-eval && npm ci
node node_modules/typescript7/bin/tsc -p repros --runExternalCode
node_modules/.bin/tsrs -p repros --runExternalCode
node node_modules/typescript7/bin/tsc -p repros/ts5088
```

## 1. microsoft/typescript-go: TS5088 where TypeScript 6 emitted an elided type (new)

No existing issue was found for `TS5088` or "cyclic structure" in microsoft/typescript-go.

> **Title:** TS5088 "cannot be trivially serialized" on a recursive type reached through `ref<T>()`, which 6.0 emitted with `/*elided*/ any`
>
> With `declaration: true`, an exported function whose inferred return type contains a recursive type alias mapped by
> Vue's `UnwrapRef` fails in 7.1.0-beta and compiles in 6.0.3.
>
> ```ts
> import {ref} from "vue" // vue@3.5.44
>
> export type Schema = {
>     properties?: Record<string, Schema>;
>     items?: Schema;
> }
>
> export function useStack() {
>     const stack = ref<{schema: Schema}[]>([])
>     return {stack}
> }
> ```
>
> - 7.1.0-beta: `index.ts(8,17): error TS5088: The inferred type of 'useStack' references a type with a cyclic structure which cannot be trivially serialized. A type annotation is necessary.`
> - 6.0.3: no error; the emitted `.d.ts` contains `items?: /*elided*/ any | undefined;`.
>
> If the error is the intended replacement for the lossy elision, a note in the 7.0 breaking-changes list would help:
> in a `composite` project the error also suppresses the file's declaration, and every downstream project then
> reports TS6305/TS7016 against it (136 follow-up diagnostics from 6 of these in our codebase).

## 2. vuejs/language-tools#6334: comment draft (known)

> Same failure at scale: 262 of 1,095 diagnostics in a ~1,900-file Vue 3.5 app are `onX` listeners (`@click`,
> `@keydown`, and component events forwarded through `$attrs` such as `@closed` / `@visible-change`) on components that
> declare no matching emit. Minimal repro: `repros/NativeListener.vue` (`<Child @click="() => {}" />` gives TS2353 on
> `onClick`). The `emits: ["click"]` workaround does not apply to wrappers that forward a third-party component's events.

## 3. vuejs/language-tools#6318: comment draft (known)

> Another data point: 151 `aria-*` / `role` diagnostics, all on design-system wrappers. `htmlAttributes` defaults to
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
