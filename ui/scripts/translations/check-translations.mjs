#!/usr/bin/env node
// Standalone translation key-parity and interpolation-placeholder check.
// Deliberately dependency-free (only Node builtins) so it can run in CI right
// after `actions/checkout`, before any `npm ci` — a translation typo shouldn't
// have to wait on a full install to be reported.
//
// This is the PR gate. `ui/scripts/translations/compareTranslations.ts` (run by
// `npm run translations:check` in both repos) checks the same placeholder rules
// against vue-i18n's real compiler, which is stricter but needs node_modules;
// it runs locally and on the nightly Auto-Translate workflow.
//
// Run as two independent, ownership-scoped checks so a failure tells you
// unambiguously which repo to fix, instead of one merged pass that requires
// guessing at attribution:
//   --scope oss  Every OSS language file has the same keys as OSS's own
//                en.json. A failure here is an OSS-repo problem - it exists
//                (and should be fixed) independently of this EE checkout.
//   --scope ee   Every EE language file has the same keys as EE's own
//                en.json, and no EE key redefines one OSS already owns. A
//                failure here is fixed in this repo's ee_translations/.
// With no --scope, both run in sequence (OSS first, then EE) for local use.
//
// Both scopes additionally verify that every message's interpolation
// placeholders are well-formed and match the English source — see the
// "Interpolation placeholders" section below.
//
// --report <path> writes a JSON summary ({missing: {lang: [keys]},
// duplicates: [keys], placeholders: {lang: [problems]}}) for
// build-translation-comment.mjs to turn into a PR comment. Always written, even
// on a passing run, so the comment builder can tell "no report" (check didn't
// run) apart from "report, but empty".
//
// Usage: node ui/scripts/translations/check-translations.mjs [--scope oss|ee] [--ee-root <path>] [--report <path>]
//
// Lives in OSS and is driven from EE (`ui-ee/scripts/translations/check-translations.mjs` is a
// thin shim that points `--ee-root` at itself), so the key-parity and placeholder rules exist in
// exactly one place rather than once per repo.

import fs from "node:fs"
import path from "node:path"
import {fileURLToPath} from "node:url"
import {flattenStrings, leafKeys, placeholderProblems} from "./translationRules.mjs"

const here = path.dirname(fileURLToPath(import.meta.url))
// ui/scripts/translations -> the OSS repo root
const ossRoot = path.resolve(here, "../../..")
const ossTranslationsDir = path.resolve(ossRoot, "ui/src/translations")

function argValue(flag) {
    const index = process.argv.indexOf(flag)
    return index !== -1 ? process.argv[index + 1] : undefined
}

function resolveEeTranslationsDir() {
    const eeRoot = argValue("--ee-root")
    if (eeRoot) return path.resolve(eeRoot, "ui-ee/src/translations/ee_translations")

    // No explicit root: assume the standard side-by-side checkout, EE beside OSS.
    return path.join(path.dirname(ossRoot), "kestra-ee", "ui-ee/src/translations/ee_translations")
}

const eeTranslationsDir = resolveEeTranslationsDir()
const scope = argValue("--scope") ?? "all"
const reportPath = argValue("--report")

function readJson(filePath) {
    return JSON.parse(fs.readFileSync(filePath, "utf-8"))
}

// Each translation file wraps its content under its own language key, e.g.
// `{"de": {...}}`. Unwrap it so key comparisons operate on the actual content.
function unwrapLanguage(obj, lang) {
    return obj && typeof obj === "object" && lang in obj ? obj[lang] : obj
}

function listLanguages(dir) {
    if (!fs.existsSync(dir)) return []
    return fs.readdirSync(dir)
        .filter(file => file.endsWith(".json"))
        .map(file => file.replace(/\.json$/, ""))
        .filter(lang => lang !== "en")
}

/** Adds `result.placeholders[lang]` for every language whose messages break the rules above. */
function checkPlaceholders(result, label, dir, fixPath) {
    const english = flattenStrings(readLanguage(dir, "en"))

    for (const lang of ["en", ...listLanguages(dir)]) {
        const messages = flattenStrings(readLanguage(dir, lang))
        const problems = Object.entries(messages).flatMap(([key, message]) =>
            // English is the source every locale is generated from, so it is checked
            // against itself: structural breakage there propagates to all of them.
            placeholderProblems(key, message, lang === "en" ? undefined : english[key]),
        )
        if (problems.length === 0) continue

        result.placeholders[lang] = problems
        for (const problem of problems) {
            annotate("error", `[${label}] Translation "${lang}": ${problem} — fix in ${fixPath.replace("{lang}", lang)}`)
        }
    }
}

function annotate(level, message) {
    console.log(`::${level}::${message}`)
}

function readLanguage(dir, lang) {
    const file = path.join(dir, `${lang}.json`)
    return fs.existsSync(file) ? unwrapLanguage(readJson(file), lang) : {}
}

// --- OSS scope: OSS languages must match OSS's own en.json ----------------
// A failure here is an OSS-repo issue - fix it in kestra-io/kestra, not here.
function checkOss() {
    const result = {missing: {}, duplicates: [], placeholders: {}}

    if (!fs.existsSync(ossTranslationsDir)) {
        annotate("warning", `OSS translations directory not found at ${ossTranslationsDir} - skipping OSS check.`)
        return result
    }

    checkPlaceholders(result, "OSS", ossTranslationsDir, "kestra-io/kestra's ui/src/translations/{lang}.json")

    const ossEn = readLanguage(ossTranslationsDir, "en")
    const ossEnKeys = leafKeys(ossEn)

    for (const lang of listLanguages(ossTranslationsDir)) {
        const langKeys = new Set(leafKeys(readLanguage(ossTranslationsDir, lang)))
        const missing = ossEnKeys.filter(key => !langKeys.has(key))
        if (missing.length === 0) continue

        result.missing[lang] = missing
        for (const key of missing) {
            annotate("error", `[OSS] Translation "${lang}" is missing key "${key}" - fix in kestra-io/kestra's ui/src/translations/${lang}.json`)
        }
    }

    return result
}

// --- EE scope: EE languages must match EE's own en.json, and no EE key ----
// may shadow one OSS already defines. A failure here is fixed in this repo.
function checkEe() {
    const result = {missing: {}, duplicates: [], placeholders: {}}
    checkPlaceholders(result, "EE", eeTranslationsDir, "ui-ee/src/translations/ee_translations/{lang}.json")
    const eeEn = readLanguage(eeTranslationsDir, "en")
    const eeEnKeys = leafKeys(eeEn)

    for (const lang of listLanguages(eeTranslationsDir)) {
        const langKeys = new Set(leafKeys(readLanguage(eeTranslationsDir, lang)))
        const missing = eeEnKeys.filter(key => !langKeys.has(key))
        if (missing.length === 0) continue

        result.missing[lang] = missing
        for (const key of missing) {
            annotate("error", `[EE] Translation "${lang}" is missing key "${key}" - fix in ui-ee/src/translations/ee_translations/${lang}.json`)
        }
    }

    if (fs.existsSync(path.join(ossTranslationsDir, "en.json"))) {
        const ossEnKeys = new Set(leafKeys(readLanguage(ossTranslationsDir, "en")))
        const duplicates = eeEnKeys.filter(key => ossEnKeys.has(key))
        if (duplicates.length > 0) {
            result.duplicates = duplicates
            for (const key of duplicates) {
                annotate("error", `[EE] Translation key "${key}" duplicates an existing OSS key - remove it from ui-ee/src/translations/ee_translations/en.json`)
            }
        }
    } else {
        annotate("warning", `OSS translations directory not found at ${ossTranslationsDir} - skipping EE/OSS duplication check.`)
    }

    return result
}

function hasIssues(result) {
    return Object.keys(result.missing).length > 0 || result.duplicates.length > 0 || Object.keys(result.placeholders).length > 0
}

const report = {scope, missing: {}, duplicates: [], placeholders: {}}
let hasFailure = false

if (scope === "oss" || scope === "all") {
    const result = checkOss()
    hasFailure = hasFailure || hasIssues(result)
    Object.assign(report.missing, result.missing)
    Object.assign(report.placeholders, result.placeholders)
    report.duplicates.push(...result.duplicates)
}
if (scope === "ee" || scope === "all") {
    const result = checkEe()
    hasFailure = hasFailure || hasIssues(result)
    Object.assign(report.missing, result.missing)
    Object.assign(report.placeholders, result.placeholders)
    report.duplicates.push(...result.duplicates)
}

if (reportPath) {
    fs.mkdirSync(path.dirname(path.resolve(reportPath)), {recursive: true})
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2))
}

if (hasFailure) {
    console.error("\nTranslation check failed - see ::error:: annotations above.")
    process.exit(1)
}

console.log(`Translation check passed (scope: ${scope}).`)
