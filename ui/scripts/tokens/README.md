# `--ks-*` token check

```bash
cd ui && npm run tokens:check
```

Reports every `var(--ks-…)` in `src/` and `packages/` whose name is declared nowhere, and proposes a
replacement for each.

## Why it exists

An unresolvable custom property with no fallback does not fall back to anything: the whole
declaration is invalid at computed-value time, so the property is dropped and the element inherits
instead. Nothing is logged, and the page keeps rendering. A misspelled or retired token therefore
looks fine until someone measures the computed style.

That is how the interval filter's **Apply to** row shipped with no visible selection for months
(kestra-io/kestra#18777): `.date-filter-option.active` painted with `--ks-primary` and
`--ks-background-card`, neither of which has ever been declared, so the selected option computed
identically to the unselected ones.

## What counts as declared

- every name in `packages/design-system/tests/storybook/Basic/Color-variables.json`, which
  `packages/design-system/scripts/generate-palette.mjs` writes from the Figma palette and is the
  source of truth for colour
- every `--ks-*` declared anywhere under the scanned roots: plain CSS, SCSS's `#{--name}:` form, a
  quoted key in a JS style object, or a runtime `element.style.setProperty("--name", …)`

Names built by interpolation (`var(--ks-status-#{$state})`) are skipped, since their value is only
known at runtime.

## Severity

- **no fallback** — the declaration is dropped. Reported, and the command exits non-zero.
- **with a fallback** (`var(--ks-content-link, var(--ks-text-link))`) — it renders, but the first
  name is probably wrong. Reported without failing.

## Adding a token

Do not declare a colour by hand. The `--ks-*` colour tokens are generated from Figma, and a
hand-written declaration is disconnected from the theme it is supposed to follow: it will not change
when the palette does, and it will not have a dark-mode counterpart. Add it in Figma and regenerate,
or use a token that already exists.

`RETIRED` in `checkTokens.ts` maps names removed by a rename onto their replacements. Add an entry
there whenever a token is renamed, so the check can propose the replacement instead of the nearest
string.
