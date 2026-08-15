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
//                en.json - for the app's messages and for the design system's,
//                which ships its own per-language files. A failure here is an
//                OSS-repo problem - it exists (and should be fixed)
//                independently of this EE checkout.
//   --scope ee   Every EE language file has the same keys as EE's own
//                en.json, and no EE key redefines one OSS already owns. A
//                failure here is fixed in this repo's ee_translations/.
// With no --scope, both run in sequence (OSS first, then EE) for local use.
//
// Both scopes additionally verify that every message's interpolation
// placeholders are well-formed and match the English source, and that no
// non-Latin-script locale is still carrying the untouched English text - the
// shape a failed generator run leaves behind, which every other check passes.
//
// --report <path> writes a JSON summary ({missing: {lang: [keys]},
// duplicates: [keys], placeholders: {lang: [problems]},
// untranslated: {lang: [keys]}}) for build-comment.mjs to turn into a PR
// comment. Always written, even on a passing run, so the comment builder can
// tell "no report" (check didn't run) apart from "report, but empty".
//
// Usage: node ui/scripts/translations/check-translations.mjs [--scope oss|ee] [--ee-root <path>] [--report <path>]
//
// Lives in OSS and is driven from EE (`ui-ee/scripts/translations/check-translations.mjs` is a
// thin shim that points `--ee-root` at itself), so the key-parity and placeholder rules exist in
// exactly one place rather than once per repo.

import fs from "node:fs"
import path from "node:path"
import {fileURLToPath} from "node:url"
import {flattenStrings, leafKeys, placeholderProblems, shadowedOssKeys, untranslatedKeys} from "./translationRules.mjs"
import {staleKeys} from "./fingerprintRules.mjs"

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

// The design system ships its own per-language files so a language costs one chunk; they are
// checked exactly like the app's. OSS-only - EE has no design-system messages of its own.
const designSystemTranslationsDir = path.resolve(ossRoot, "ui/packages/design-system/src/translations")

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

        // Appended, not assigned: a language appears once per directory checked, and the app's
        // problems must not be dropped by the design system's.
        ;(result.placeholders[lang] ??= []).push(...problems)
        for (const problem of problems) {
            annotate("error", `[${label}] Translation "${lang}": ${problem} — fix in ${fixPath.replace("{lang}", lang)}`)
        }
    }
}

/**
 * Adds `result.untranslated[lang]` for every language still carrying the English text verbatim.
 *
 * Only the non-Latin-script locales are examined, and only prose is considered - see
 * `untranslatedKeys` in ./translationRules.mjs for why.
 */
function checkUntranslated(result, label, dir, fixPath) {
    const english = flattenStrings(readLanguage(dir, "en"))

    for (const lang of listLanguages(dir)) {
        const keys = untranslatedKeys(lang, flattenStrings(readLanguage(dir, lang)), english)
        if (keys.length === 0) continue

        ;(result.untranslated[lang] ??= []).push(...keys)
        annotate("error", `[${label}] Translation "${lang}" still holds the English text for ${keys.length} key(s): ${keys.join(", ")} - re-translate them in ${fixPath.replace("{lang}", lang)} by blanking the values and running \`npm run translations:generate\``)
    }
}

function annotate(level, message) {
    console.log(`::${level}::${message}`)
}

function readLanguage(dir, lang) {
    const file = path.join(dir, `${lang}.json`)
    return fs.existsSync(file) ? unwrapLanguage(readJson(file), lang) : {}
}

// --- Key parity and staleness -------------------------------------------
// Every language file must carry the same keys as the English one it was generated from.
function checkKeyParity(result, label, dir, fixPath) {
    const englishKeys = leafKeys(readLanguage(dir, "en"))

    for (const lang of listLanguages(dir)) {
        const langKeys = new Set(leafKeys(readLanguage(dir, lang)))
        const missing = englishKeys.filter(key => !langKeys.has(key))
        if (missing.length === 0) continue

        ;(result.missing[lang] ??= []).push(...missing)
        for (const key of missing) {
            annotate("error", `[${label}] Translation "${lang}" is missing key "${key}" - fix in ${fixPath.replace("{lang}", lang)}`)
        }
    }
}

/**
 * Keys whose English source changed after the other languages were generated from it. Key parity
 * alone never caught those - the translation is there, just of text that no longer exists.
 */
function checkStale(result, label, dir, fingerprintsFile, fixCommand) {
    if (!fs.existsSync(fingerprintsFile)) return

    const stale = staleKeys(readLanguage(dir, "en"), JSON.parse(fs.readFileSync(fingerprintsFile, "utf-8")))
    if (stale.length === 0) return

    result.stale.push(...stale)
    for (const key of stale) {
        annotate("error", `[${label}] Translation key "${key}" has no up-to-date translation - it is either new, or its English source changed after it was translated. Run \`${fixCommand}\``)
    }
}

// --- OSS scope: OSS languages must match OSS's own en.json ----------------
// A failure here is an OSS-repo issue - fix it in kestra-io/kestra, not here.
function checkOss() {
    const result = {missing: {}, duplicates: [], placeholders: {}, stale: [], untranslated: {}}

    if (!fs.existsSync(ossTranslationsDir)) {
        annotate("warning", `OSS translations directory not found at ${ossTranslationsDir} - skipping OSS check.`)
        return result
    }

    const appFixPath = "kestra-io/kestra's ui/src/translations/{lang}.json"
    checkPlaceholders(result, "OSS", ossTranslationsDir, appFixPath)
    checkUntranslated(result, "OSS", ossTranslationsDir, appFixPath)
    checkKeyParity(result, "OSS", ossTranslationsDir, appFixPath)

    const designSystemFixPath = "kestra-io/kestra's ui/packages/design-system/src/translations/{lang}.json"
    checkPlaceholders(result, "OSS design system", designSystemTranslationsDir, designSystemFixPath)
    checkUntranslated(result, "OSS design system", designSystemTranslationsDir, designSystemFixPath)
    checkKeyParity(result, "OSS design system", designSystemTranslationsDir, designSystemFixPath)
    checkStale(result, "OSS design system", designSystemTranslationsDir, path.join(here, "fingerprints-design-system.json"), "npm run translations:generate")

    return result
}

// --- EE scope: EE languages must match EE's own en.json, and no EE key ----
// may shadow one OSS already defines. A failure here is fixed in this repo.
function shadowMessage(key, ossKey, kind) {
    switch (kind) {
    case "nested-under-oss-leaf":
        return `Translation key "${key}" nests under "${ossKey}", which OSS defines as a message: merging EE over OSS replaces that message with an object, and vue-i18n then renders "${ossKey}" as a raw key instead of a label. Rename the EE namespace`
    case "replaces-oss-namespace":
        return `Translation key "${key}" is a message, but OSS uses "${ossKey}" as a namespace for its own keys, which merging EE over OSS would hide. Rename the EE key`
    default:
        return `Translation key "${key}" duplicates an existing OSS key - remove it`
    }
}

function checkEe() {
    const result = {missing: {}, duplicates: [], placeholders: {}, stale: [], untranslated: {}}
    const eeFixPath = "ui-ee/src/translations/ee_translations/{lang}.json"
    checkPlaceholders(result, "EE", eeTranslationsDir, eeFixPath)
    checkUntranslated(result, "EE", eeTranslationsDir, eeFixPath)
    checkKeyParity(result, "EE", eeTranslationsDir, eeFixPath)
    const eeEnKeys = leafKeys(readLanguage(eeTranslationsDir, "en"))

    if (fs.existsSync(path.join(ossTranslationsDir, "en.json"))) {
        const shadowed = shadowedOssKeys(eeEnKeys, leafKeys(readLanguage(ossTranslationsDir, "en")))
        if (shadowed.length > 0) {
            result.duplicates = shadowed.map(({key}) => key)
            for (const {key, ossKey, kind} of shadowed) {
                annotate("error", `[EE] ${shadowMessage(key, ossKey, kind)} - fix it in ui-ee/src/translations/ee_translations/en.json`)
            }
        }
    } else {
        annotate("warning", `OSS translations directory not found at ${ossTranslationsDir} - skipping EE/OSS duplication check.`)
    }

    return result
}

function hasIssues(result) {
    return Object.keys(result.missing).length > 0
        || result.duplicates.length > 0
        || Object.keys(result.placeholders).length > 0
        || result.stale.length > 0
        || Object.keys(result.untranslated).length > 0
}

const report = {scope, missing: {}, duplicates: [], placeholders: {}, stale: [], untranslated: {}}
let hasFailure = false

if (scope === "oss" || scope === "all") {
    const result = checkOss()
    hasFailure = hasFailure || hasIssues(result)
    Object.assign(report.missing, result.missing)
    Object.assign(report.placeholders, result.placeholders)
    report.duplicates.push(...result.duplicates)
    report.stale.push(...result.stale)
    Object.assign(report.untranslated, result.untranslated)
}
if (scope === "ee" || scope === "all") {
    const result = checkEe()
    hasFailure = hasFailure || hasIssues(result)
    Object.assign(report.missing, result.missing)
    Object.assign(report.placeholders, result.placeholders)
    report.duplicates.push(...result.duplicates)
    report.stale.push(...result.stale)
    Object.assign(report.untranslated, result.untranslated)
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
