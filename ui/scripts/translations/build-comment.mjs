#!/usr/bin/env node
// Turns the JSON reports written by check-translations.mjs (--report) into a
// single Markdown PR comment body. Prints nothing (exit 0) if both reports
// are clean, so the workflow can skip posting/updating a comment.
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
        "### ❌ EE translations - duplicated keys\n\n" +
        eeReport.duplicates.map(key => `- \`${key}\``).join("\n") + "\n\n" +
        "**What to do:** these keys already exist in OSS. Remove them from " +
        "`ui-ee/src/translations/ee_translations/en.json` (and the other EE language files) - the OSS key already covers them.",
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

if (sections.length === 0) {
    process.exit(0)
}

console.log(`## 🌐 Translation check failed\n\n${sections.join("\n\n")}`)
