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
| `stylelintPlugin.mjs` | stylelint, `ks/no-undeclared-custom-property` | `<style>` blocks in `.vue`, plus `.scss` and `.css` |
| `eslintPlugin.mjs` | eslint, `kestra-tokens/no-undeclared-ks-token` | `cssVar("--ks-…")` and `var(--ks-…)` written inside a string |

Both read the same `knownTokens.mjs`, so they agree on what exists and phrase the report the same
way. `npm run lint` and `npm run test:lint` run both.

`stylelint.config.mjs` and a `ks/custom-property-pattern-usage` plugin existed here from #6645 until
#19084 deleted them, dormant the whole time because stylelint itself was never a dependency. This
brings back a config that only carries the token rule, so the linter reports one thing and reports it
for a reason; the 269 hex colours and 158 non-`--ks` properties the old config would have flagged are
real debt, but enforcing them is a separate job with a separate cleanup.

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

EE will need to set `KS_TOKEN_ROOTS=/path/to/ui-ee/src` so its own declarations count too. Nothing
sets it yet: `ui-ee/eslint.config.js` spreads this config, so the eslint half goes live there as soon
as this lands, and EE declares no `--ks-*` of its own today.

## Known gaps

- `var(--ks-…)` inside a template expression (`:style="{color: 'var(--ks-…)'}"`) is seen by neither
  half: stylelint reads `<style>` blocks, and the eslint rule walks the script, not the
  `vue-eslint-parser` template body. There are 46 such usages and all of them resolve today.
- `.jsx` and `.tsx` are covered by the eslint half only, since stylelint cannot read a style written
  as a JSX attribute.

## Adding a token

Do not declare a colour by hand. The `--ks-*` colour tokens are generated from Figma, and a
hand-written declaration is disconnected from the theme it is supposed to follow: it will not change
when the palette does, and it will not have a dark-mode counterpart. Add it in Figma and regenerate,
or use a token that already exists.

`RETIRED` in `knownTokens.mjs` maps names removed by a rename onto their replacements. Add an entry
whenever a token is renamed, so the rule can propose the replacement instead of the nearest string.
