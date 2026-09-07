# `--ks-*` token rule

An undeclared `var(--ks-…)` with no fallback does not fall back to anything: the whole declaration
is invalid at computed-value time, so the property is dropped and the element inherits instead.
Nothing is logged, and the page keeps rendering. A misspelled or retired token therefore looks fine
until someone measures the computed style.

That is how the interval filter's **Apply to** row shipped with no visible selection
(kestra-io/kestra#18777): `.date-filter-option.active` painted with `--ks-primary` and
`--ks-background-card`, neither of which has ever been declared, so the selected option computed
identically to the unselected ones.

The rule reports those as you type. There is no separate command to remember.

## Where it runs

| | linter | covers |
|---|---|---|
| `../../plugins/lint-custom-properties.mjs` | stylelint, `ks/no-undeclared-custom-property` | `<style>` blocks in `.vue`, plus `.scss` and `.css` |
| `eslintPlugin.mjs` | eslint, `kestra-tokens/no-undeclared-ks-token` | `cssVar("--ks-…")` and `var(--ks-…)` written inside a string |

Both read the same `knownTokens.mjs`, so they agree on what exists and phrase the report the same
way. `npm run lint` and `npm run test:lint` run both.

The stylelint half sits in the plugin that already held `ks/custom-property-pattern-usage`, and
reuses its `var()` walking and index arithmetic.

`stylelint.config.mjs` predates this and had been dormant, since stylelint itself was not installed:
it reports 269 hex colours and 158 non-`--ks` custom properties across the repo. Those stay as
warnings, visible while editing and ignored by the build; only `ks/no-undeclared-custom-property` is
an error, because it is not a style preference but a declaration the browser throws away. Clearing
the debt and promoting the rest is a separate job.

## In your editor

ESLint needs nothing. Stylelint needs the
[Stylelint extension](https://marketplace.visualstudio.com/items?itemName=stylelint.vscode-stylelint),
which finds `ui/stylelint.config.mjs` on its own for `.css` and `.scss`. To have it lint `<style>`
blocks in `.vue` too, add to your workspace settings:

```json
{
    "stylelint.validate": ["css", "scss", "vue"]
}
```

WebStorm ships stylelint support: enable it under Languages & Frameworks → Style Sheets → Stylelint.

## What counts as declared

- every name in `packages/design-system/tests/storybook/Basic/Color-variables.json`, which
  `packages/design-system/scripts/generate-palette.mjs` writes from the Figma palette and which is
  the source of truth for colour
- every `--ks-*` declared anywhere under `src/` and `packages/`: plain CSS, SCSS's `#{--name}:` form,
  a quoted key in a JS style object, or a runtime `element.style.setProperty("--name", …)`
- everything the file being linted declares itself, read from the buffer rather than from disk, so
  a token you have just typed is not reported while the file is unsaved

Names built by interpolation (`var(--ks-status-#{$state})`) are skipped, since their value is only
known at runtime.

EE sets `KS_TOKEN_ROOTS=/path/to/ui-ee/src` so its own declarations count too.

## Adding a token

Do not declare a colour by hand. The `--ks-*` colour tokens are generated from Figma, and a
hand-written declaration is disconnected from the theme it is supposed to follow: it will not change
when the palette does, and it will not have a dark-mode counterpart. Add it in Figma and regenerate,
or use a token that already exists.

`RETIRED` in `knownTokens.mjs` maps names removed by a rename onto their replacements. Add an entry
whenever a token is renamed, so the rule can propose the replacement instead of the nearest string.
