#!/usr/bin/env node
// Turns the JSON reports written by check-translations.mjs (--report) into a
// single Markdown PR comment body. Prints nothing (exit 0) if both reports are
// clean, which the workflow hands to comment-update as an empty template so the
// section a failing run left on the PR is cleared rather than outliving the fix.
//
// Usage: node ui-ee/scripts/translations/build-comment.mjs <oss-report.json> <ee-report.json>

import fs from "node:fs"

const [ossReportPath, eeReportPath] = process.argv.slice(2)

function readReport(filePath) {
    if (!filePath || !fs.existsSync(filePath)) return null
    return JSON.parse(fs.readFileSync(filePath, "utf-8"))
}

function formatMissing(missing) {
    return Object.entries(missing)
        .map(([lang, keys]) => `- \`${lang}\`: ${keys.map(key => `\`${key}\``).join(", ")}`)
        .join("\n")
}

function formatPlaceholders(placeholders) {
    return Object.entries(placeholders)
        .map(([lang, problems]) => `- \`${lang}\`\n` + problems.map(problem => `  - ${problem}`).join("\n"))
        .join("\n")
}

// Tolerates reports written before `placeholders` existed, so a stale report from an
// in-flight run doesn't crash the comment build.
const placeholdersOf = (report) => report?.placeholders ?? {}

const ossReport = readReport(ossReportPath)
const eeReport = readReport(eeReportPath)

const sections = []

if (ossReport && Object.keys(ossReport.missing).length > 0) {
    sections.push(
        "### ❌ OSS translations\n\n" +
        "**Missing keys:**\n" +
        formatMissing(ossReport.missing) + "\n\n" +
        "**What to do:** these keys are missing upstream, in [kestra-io/kestra](https://github.com/kestra-io/kestra). " +
        "Merge the translation PR there (or wait for it to merge) and rerun this check.",
    )
}

if (eeReport && Object.keys(eeReport.missing).length > 0) {
    sections.push(
        "### ❌ EE translations - missing keys\n\n" +
        formatMissing(eeReport.missing) + "\n\n" +
        "**What to do:** let the translation generation step fill these in - run `npm run translations:generate` " +
        "in `ui-ee` (or trigger the `Auto-Translate UI keys` workflow), then commit the result.",
    )
}

if (eeReport && eeReport.duplicates.length > 0) {
    sections.push(
        "### ❌ EE translations - keys colliding with OSS\n\n" +
        eeReport.duplicates.map(key => `- \`${key}\``).join("\n") + "\n\n" +
        "**What to do:** each of these EE keys collides with a key OSS already owns - as an exact duplicate, " +
        "or by sitting above or below an OSS message in the key tree, which the EE-over-OSS merge turns into a raw " +
        "key rendered in the UI. Remove the duplicate (the OSS key already covers it) or rename the EE key or namespace in " +
        "`ui-ee/src/translations/ee_translations/en.json`, then regenerate the other languages. The CI annotations " +
        "name the exact OSS key each one collides with.",
    )
}

if (Object.keys(placeholdersOf(ossReport)).length > 0) {
    sections.push(
        "### ❌ OSS translations - broken interpolation placeholders\n\n" +
        formatPlaceholders(placeholdersOf(ossReport)) + "\n\n" +
        "**What to do:** fix these upstream, in [kestra-io/kestra](https://github.com/kestra-io/kestra)'s `ui/src/translations/`. " +
        "vue-i18n interpolates a single pair of braces (`{name}`); `{{name}}` is a compile error, so `t()` throws and the " +
        "component rendering the key fails outright. Each translation must carry exactly the placeholders its English source declares.",
    )
}

if (Object.keys(placeholdersOf(eeReport)).length > 0) {
    sections.push(
        "### ❌ EE translations - broken interpolation placeholders\n\n" +
        formatPlaceholders(placeholdersOf(eeReport)) + "\n\n" +
        "**What to do:** fix these in `ui-ee/src/translations/ee_translations/`. vue-i18n interpolates a single pair of " +
        "braces (`{name}`); `{{name}}` is a compile error, so `t()` throws and the component rendering the key fails outright. " +
        "Rather than hand-editing a non-English string, empty the value and rerun `npm run translations:generate`.",
    )
}

// Tolerates reports written before `undefinedKeys` existed, like `placeholdersOf` above.
const undefinedKeysOf = (report) => report?.undefinedKeys ?? []

function formatUndefinedKeys(findings) {
    return findings.map(({file, line, key}) => `- \`${key}\` in \`${file}:${line}\``).join("\n")
}

if (undefinedKeysOf(ossReport).length > 0) {
    sections.push(
        "### ❌ OSS translations - keys used in code but defined nowhere\n\n" +
        formatUndefinedKeys(undefinedKeysOf(ossReport)) + "\n\n" +
        "**What to do:** fix these upstream, in [kestra-io/kestra](https://github.com/kestra-io/kestra). Each key is passed " +
        "to `t()` but exists in no `en.json`, so the UI renders the raw key id. Add it to `ui/src/translations/en.json` " +
        "(or to the owning design-system `*.locale.ts`), or point the call at an existing key, then run `npm run translations:generate`.",
    )
}

if (undefinedKeysOf(eeReport).length > 0) {
    sections.push(
        "### ❌ EE translations - keys used in code but defined nowhere\n\n" +
        formatUndefinedKeys(undefinedKeysOf(eeReport)) + "\n\n" +
        "**What to do:** each key is passed to `t()` but exists in neither `ui-ee/src/translations/ee_translations/en.json` " +
        "nor OSS's `en.json`, so the UI renders the raw key id. Add it to the EE `en.json` (or point the call at an existing key), " +
        "then run `npm run translations:generate` in `ui-ee`.",
    )
}

if (sections.length === 0) {
    process.exit(0)
}

console.log(`## 🌐 Translation check failed\n\n${sections.join("\n\n")}`)
