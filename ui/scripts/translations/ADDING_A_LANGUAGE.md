# Adding a New UI Language to Kestra (OSS + EE)

This guide covers everything needed to ship a new UI locale end to end, based on the state of the translations pipeline after the 2026-08 unification (fingerprints + shared tooling, https://github.com/kestra-io/kestra/pull/18042 + https://github.com/kestra-io/kestra-ee/pull/9813). For how the pipeline itself works, see [README.md](README.md).

## 1. Current state

**13 locales ship today, identical in OSS and EE** - English plus 12 generated languages:

| Code | Language | Notes |
|------|----------|-------|
| `en` | English | Source of truth, never generated |
| `de` | German | Tone + glossary overhauled 2026-08: informal du-form, entity glossary, kill/stop distinction (https://github.com/kestra-io/kestra/pull/18207) |
| `es` | Spanish | |
| `fr` | French | |
| `hi` | Hindi | |
| `it` | Italian | |
| `ja` | Japanese | |
| `ko` | Korean | |
| `pl` | Polish | Tone + declension overhauled 2026-08 (https://github.com/kestra-io/kestra/pull/18212, EE https://github.com/kestra-io/kestra-ee/pull/9984); custom plural rule (`polishPluralIndex` in `i18n.ts`) |
| `pt` | Portuguese | |
| `pt_BR` | Portuguese (Brazil) | Moment locale key differs: `pt-br` |
| `ru` | Russian | Three-form plurals, deliberately left on the default rule (unreviewed) |
| `zh_CN` | Simplified Chinese | Moment locale key differs: `zh-cn` |

Volume per new language (as of 2026-08): **~1,900 OSS keys** (`ui/src/translations/en.json`) + **~1,900 EE keys** (`ui-ee/src/translations/ee_translations/en.json` in EE) + the design-system `*.locale.ts` strings. All of it is generated via Gemini, one request per key, so a full new language is roughly 4,000 API calls - plan for the generator to run for a long time (run it in the background).

## 2. Prerequisites

- OSS (`kestra/`) and EE (`kestra-ee/`) checked out side by side - the EE tooling resolves the OSS checkout at runtime.
- `GEMINI_API_KEY` available (do not print it).

## 3. OSS steps (`kestra/`)

Running example: Turkish (`tr`).

### 3.1 Declare the locale - the single source of truth

Add the language to `LANGUAGES` in `ui/src/translations/languages.ts`, keeping alphabetical order:

```ts
["tr", "Turkish"],
```

This one entry drives almost everything:

- the generator (`generateTranslations.ts` imports `LANGUAGES` and will create `tr.json` from scratch, and add a `tr` section to every design-system `*.locale.ts` file),
- the checker (`compareTranslations.ts` via `TRANSLATED_LOCALES`),
- the app's `SUPPORT_LOCALES` (runtime locale loading - `i18n.ts` picks up `./tr.json` through its `import.meta.glob`, no wiring needed),
- the dependency-free PR gate discovers locale files by scanning the directory, so the new JSON is checked automatically.

The English name in the pair doubles as the target-language instruction sent to Gemini, so make it descriptive (this is why the list says "Simplified Chinese (Mandarin)" rather than an endonym).

### 3.2 Moment locale loader

Add a loader to `MOMENT_LOCALE_LOADERS` in `ui/src/utils/init.ts`:

```ts
tr: () => import("moment/dist/locale/tr"),
```

The key must be moment's normalized form: lowercase with `-` instead of `_` (`pt_BR` -> `pt-br`, `zh_TW` -> `zh-tw`). The lookup lowercases and replaces underscores before resolving, so a wrongly cased key silently falls back to English dates.

### 3.3 Settings language selector

Add the option to `langOptions` in `ui/src/components/settings/BasicSettings.vue`:

```ts
{value: "tr", text: "Turkish"},
```

This list is hand-maintained and does NOT derive from `languages.ts` - forgetting it means the language works but nobody can select it.

### 3.4 Plural rule (only if the language needs one)

vue-i18n's default rule handles two-form languages (and `tr`, `vi`, `id`, `zh_TW` are fine with it). For a language with three or more plural forms (Slavic family, Arabic), add a custom rule to the `pluralRules` option in `ui/src/translations/i18n.ts`, next to `polishPluralIndex`, and have a native speaker review the three-form messages before enabling it - see the Russian comment there for why an unreviewed rule is worse than the default.

### 3.5 Write the language's generator rules BEFORE generating

The generator prompt in `ui/scripts/translations/generateTranslations.ts` carries a per-language rule block for German and Polish: form of address (informal du-form German, informal second-person-singular Polish), an entity glossary (which Kestra nouns stay English, which get a native equivalent), declension/animacy rules for inflected languages, capitalization, and disambiguations like keeping "kill" and "stop" apart. German and Polish shipped for months WITHOUT these rules and both needed a full retroactive sweep in 2026-08 to fix the tonality (German https://github.com/kestra-io/kestra/pull/18207, Polish https://github.com/kestra-io/kestra/pull/18212 + EE https://github.com/kestra-io/kestra-ee/pull/9984) - per-key generation with no tone rules means Gemini picks formal or informal per key, and the result is inconsistent across the UI.

So before running the generator for a new language, add a rule block for it to the prompt, deciding at minimum:

- **Form of address** - formal vs informal, and what bare action labels use (infinitive vs imperative). Most modern dev-tool UIs go informal (Turkish "sen", not "siz").
- **Reserved-term handling** - how the untranslatable English terms (`flow`, `task`, `namespace`, ...) behave in the language: inflection/suffixing rules (critical for agglutinative Turkish: "Flow'u", "Task'lar" with apostrophe-suffix convention), gender assignment for gendered languages, capitalization.
- **Entity glossary** - which Kestra entity nouns beyond the reserved list stay English vs get a native equivalent (use the German and Polish blocks as templates; the two made different calls, e.g. Execution stays English in German but is "egzekucja" in Polish).

Have a native speaker review the rule block and the first generated batch - the rules are cheap to write before generation and expensive to retrofit after.

### 3.6 Generate

```bash
cd ui
GEMINI_API_KEY="..." npm run translations:generate
```

Run it in the background - a full language is ~2,000 OSS keys plus every design-system `*.locale.ts` file, at one Gemini request per key. It produces:

- `ui/src/translations/tr.json` (new file, key order mirroring `en.json`),
- updated `ui/scripts/translations/fingerprints.json`,
- a `tr` section in every `ui/packages/design-system/**/*.locale.ts` file,
- updated `ui/scripts/translations/fingerprints-design-system.json`.

Commit the locale files and the fingerprints files **together** - one without the other makes every key look stale forever and the auto-translate bot will loop opening the same PR.

If a handful of keys fail on every retry with `PROHIBITED_CONTENT`, that is a Gemini safety block, not a flake - reword the English source, never hand-write the translation.

### 3.7 Verify

```bash
cd ui
npm run translations:check   # every language incl. tr: No missing / No extra / No stale keys
npm run check:types
```

Then a manual smoke test: `npm run dev`, switch the language in Settings, check a date-heavy page (Executions) so both vue-i18n and the moment locale are exercised, and check pagination and empty states (design-system strings).

## 4. EE steps (`kestra-ee/`)

The EE tooling imports the shared generator from the sibling OSS checkout, so it sees the new `LANGUAGES` entry automatically once the OSS change exists locally. Two things are EE-specific:

### 4.1 Locale wrapper file

Create `ui-ee/src/translations/tr.ts`, mirroring the existing ones:

```ts
import tr from "kestra/src/translations/tr.json" with { type: "json" }
import tr_ee from "./ee_translations/tr.json" with { type: "json" }

import _merge from "lodash/merge"

export default _merge(tr, tr_ee)
```

`translations.ts` picks it up automatically through its `import.meta.glob("./*.ts")` - no registration needed. This wrapper is the reason EE merges its own keys on top of the OSS locale at runtime.

### 4.2 Generate the EE locale

```bash
cd ui-ee
GEMINI_API_KEY="..." npm run translations:generate
```

Produces `ui-ee/src/translations/ee_translations/tr.json` (~1,900 keys) and updates `ui-ee/scripts/translations/fingerprints.json`. Same rule: commit both together.

### 4.3 Verify

```bash
cd ui-ee
npm run translations:check
npm run check:types
```

Manual smoke test on an EE-only surface (IAM, Tenants, Apps) to confirm the merged EE keys render.

## 5. PRs, CI, and merge order

- Two companion PRs with the same branch name. Suggested commits:
  - OSS: `feat(core): add Turkish as a supported UI language`
  - EE: `feat(core): add Turkish as a supported UI language`
- **Merge OSS first.** EE CI checks out OSS `develop` (or passes `--oss-root`), and the EE wrapper imports `kestra/src/translations/tr.json` from the sibling checkout - until the OSS PR is merged, EE CI cannot resolve the new locale and both the build and the translation gate fail.
- The PR gate (`check-translations.mjs`) runs on both PRs before `npm ci`; the full `translations:check` runs locally and on the auto-translate workflow.
- After both merge, the scheduled auto-translate bot (every 3h on weekdays, both repos) keeps the new language filled as English keys evolve - no ongoing manual work.
- No backport: a new language is a feature and ships from `develop` only.

## 6. Checklist

OSS:
- [ ] `languages.ts`: `LANGUAGES` entry
- [ ] `init.ts`: `MOMENT_LOCALE_LOADERS` entry (moment-normalized key)
- [ ] `BasicSettings.vue`: `langOptions` entry
- [ ] `i18n.ts`: plural rule, only if the language needs one
- [ ] `generateTranslations.ts`: per-language rule block (form of address, reserved-term inflection, entity glossary) written and reviewed before generating
- [ ] `translations:generate` run; locale JSON + `fingerprints.json` + design-system `*.locale.ts` + `fingerprints-design-system.json` committed together
- [ ] `translations:check` + `check:types` green
- [ ] Manual smoke test (language switch, dates, pagination, empty states)

EE:
- [ ] `ui-ee/src/translations/<code>.ts` wrapper created
- [ ] `translations:generate` run; `ee_translations/<code>.json` + EE `fingerprints.json` committed together
- [ ] `translations:check` + `check:types` green
- [ ] Manual smoke test on an EE-only page
- [ ] OSS PR merged before the EE PR

## 7. Gotchas

- **Never hand-write or placeholder a non-English file.** Copying English into a locale used to sneak past the old key-parity check; it now fails both the staleness and the untranslated-copy checks.
- **Fingerprint conflicts**: two branches touching `en.json` always conflict on `fingerprints.json`. Never hand-merge or pick a side - take one side as a placeholder, re-run `translations:generate`, confirm `translations:check` is clean.
- **Reserved terms stay English** (`flow`, `namespace`, `tenant`, `task`, `trigger`, `id`, ...) and ALL-CAPS status labels stay English - the generator prompt enforces this, but review the first generated batch of a new language for it anyway.
- **Placeholders**: single-brace `{name}` only; each translation must carry exactly the English source's placeholders. `{{name}}` is a vue-i18n compile error that throws at render time.
- **Element Plus is not localized per language** - the only `ElConfigProvider` (in `KsPagination`) is pinned to English on purpose. No per-language work there.
- The fingerprint key paths join with `|` while checker key paths join with `.` - they are not interchangeable; mixing them once reported 1,249 healthy keys as stale.
