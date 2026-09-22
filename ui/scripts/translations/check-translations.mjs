#!/usr/bin/env node
/**
 * The translation PR gate: key parity, placeholders, staleness, and the keys the source uses or no
 * longer uses. Dependency-free, so CI runs it straight after `actions/checkout`, before `npm ci`.
 * Lives in OSS and is driven from EE through a shim that points `--ee-root` at itself, so both repos
 * apply one set of rules.
 *
 * The two scopes are checked independently, so a failure names the repo to fix rather than needing
 * attribution; with no `--scope`, both run, OSS first. `--report <path>` writes the JSON summary
 * build-comment.mjs turns into a PR comment, on a passing run too, so "no report" and "report, but
 * empty" stay distinguishable. `--unused-candidates` prints the review list of keys with no literal
 * `t()` call and exits 0.
 *
 * Usage: node ui/scripts/translations/check-translations.mjs [--scope oss|ee] [--ee-root <path>] [--report <path>] [--unused-candidates]
 *
 * @see ./README.md for what each check catches, and why the reverse check reads "reachable" so widely
 */

import fs from "node:fs"
import path from "node:path"
import {fileURLToPath} from "node:url"
import {allKeys, flattenStrings, leafKeys, placeholderProblems, shadowedOssKeys, untranslatedKeys} from "./translationRules.mjs"
import {evalLocaleModule, staleLocaleEntries, untranslatedLocaleEntries} from "./localeFiles.mjs"
import {collectKeyEvidence, emptyEvidence, isScannedSourceFile, translationKeyUsages, translationNamespaceUsages, undefinedKeyUsages, undefinedNamespaceUsages, unusedDefinedKeys} from "./usageRules.ts"
import {staleKeys} from "./fingerprintRules.mjs"

const here = path.dirname(fileURLToPath(import.meta.url))
// ui/scripts/translations -> the OSS repo root
const ossRoot = path.resolve(here, "../../..")
const ossTranslationsDir = path.resolve(ossRoot, "ui/src/translations")
const designSystemLocaleFiles = fs.globSync(path.join(ossRoot, "ui/packages/design-system/**/*.locale.ts"))

function argValue(flag) {
    const index = process.argv.indexOf(flag)
    return index !== -1 ? process.argv[index + 1] : undefined
}

// No explicit root: assume the standard side-by-side checkout, EE beside OSS.
const eeRoot = path.resolve(argValue("--ee-root") ?? path.join(path.dirname(ossRoot), "kestra-ee"))
const eeTranslationsDir = path.join(eeRoot, "ui-ee/src/translations/ee_translations")

/**
 * A tenant type keeps its own language folder, whose keys the app roots under `tenantTypes.<type>`
 * at runtime (`ui-ee/src/translations/tenantTypeMessages.ts`). The prefix is therefore the folder
 * name, and the files themselves carry no trace of it.
 */
const eeTenantTypeTranslations = () =>
    fs.globSync(path.join(eeRoot, "ui-ee/src/tenantTypes/*/translations"))
        .map(dir => ({dir, prefix: `tenantTypes.${path.basename(path.dirname(dir))}`}))
const scope = argValue("--scope") ?? "all"
const reportPath = argValue("--report")
const unusedCandidatesOnly = process.argv.includes("--unused-candidates")

const ossSourceRoots = ["ui/src", "ui/packages/design-system/src", "ui/packages/topology/src"].map(dir => path.join(ossRoot, dir))
const eeSourceRoots = [path.join(eeRoot, "ui-ee/src")]

/**
 * Whether the OSS tree holds the code that renders keys, not just the dictionaries. The translation
 * workflows check OSS out sparsely (scripts, translations and the design system only), and there
 * `ui/src` exists while every component that calls `t()` is absent.
 */
const hasOssSources = () =>
    ossSourceRoots.every(dir => fs.existsSync(dir)) && fs.existsSync(path.join(ossRoot, "ui/src/components"))

function readJson(filePath) {
    return JSON.parse(fs.readFileSync(filePath, "utf-8"))
}

/** Each file wraps its content under its own language key, `{"de": {...}}`; comparisons want the content. */
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

/** Adds `result.placeholders[lang]` for every language whose messages break the placeholder rules. */
function checkPlaceholders(result, label, dir, fixPath) {
    const english = flattenStrings(readLanguage(dir, "en"))

    for (const lang of ["en", ...listLanguages(dir)]) {
        const messages = flattenStrings(readLanguage(dir, lang))
        const problems = Object.entries(messages).flatMap(([key, message]) =>
            // English is checked against itself: breakage in the source propagates to every locale.
            placeholderProblems(key, message, lang === "en" ? undefined : english[key]),
        )
        if (problems.length === 0) continue

        result.placeholders[lang] = problems
        for (const problem of problems) {
            annotate("error", `[${label}] Translation "${lang}": ${problem} — fix in ${fixPath.replace("{lang}", lang)}`)
        }
    }
}

/**
 * Adds `result.untranslated[lang]` for every language still carrying the English text verbatim.
 *
 * @see `untranslatedKeys` in ./translationRules.mjs for why only non-Latin-script locales and prose
 */
function checkUntranslated(result, label, dir, fixPath) {
    const english = flattenStrings(readLanguage(dir, "en"))

    for (const lang of listLanguages(dir)) {
        const keys = untranslatedKeys(lang, flattenStrings(readLanguage(dir, lang)), english)
        if (keys.length === 0) continue

        result.untranslated[lang] = keys
        annotate("error", `[${label}] Translation "${lang}" still holds the English text for ${keys.length} key(s): ${keys.join(", ")} - re-translate them in ${fixPath.replace("{lang}", lang)} by blanking the values and running \`npm run translations:generate\``)
    }
}

function annotate(level, message, location) {
    const target = location ? ` file=${location.file},line=${location.line}` : ""
    console.log(`::${level}${target}::${message}`)
}

function readLanguage(dir, lang) {
    const file = path.join(dir, `${lang}.json`)
    return fs.existsSync(file) ? unwrapLanguage(readJson(file), lang) : {}
}

/**
 * Adds `result.stale` for every key whose English text no longer matches the fingerprint its
 * translations were generated from - a new key, or an edited value that was not regenerated.
 * Key paths are the generator's `a|b|c` form, as they appear in the fingerprints file.
 */
function checkStaleJson(result, label, dir, fingerprintsFile, fixHint) {
    if (!fs.existsSync(fingerprintsFile)) return

    const stale = staleKeys(readLanguage(dir, "en"), readJson(fingerprintsFile))
    if (stale.length === 0) return

    result.stale.push(...stale)
    annotate("error", `[${label}] ${stale.length} key(s) have no up-to-date translation, either new or with an English source that changed after they were translated: ${stale.join(", ")} - ${fixHint}`)
}

/** Every key path, namespaces included, of OSS's `en.json` plus the design-system `en` blocks. */
function ossDefinedKeys() {
    const keys = new Set(allKeys(readLanguage(ossTranslationsDir, "en")))
    for (const localeFile of designSystemLocaleFiles) {
        const data = evalLocaleModule(fs.readFileSync(localeFile, "utf-8"))
        for (const key of allKeys(data.en ?? {})) keys.add(key)
    }
    return keys
}

/**
 * One pass over the source under `sourceRoots`: the key and namespace usages per file, keyed by a
 * path relative to `repoRoot` so an annotation lands on the right line, plus the evidence the
 * unused-key check reads across every file.
 */
function scanSources(repoRoot, sourceRoots, evidence = emptyEvidence()) {
    const usagesByFile = {}
    const namespacesByFile = {}

    for (const sourceRoot of sourceRoots.filter(root => fs.existsSync(root))) {
        for (const file of fs.globSync(path.join(sourceRoot, "**/*"))) {
            if (!isScannedSourceFile(file) || !fs.statSync(file).isFile()) continue

            const source = fs.readFileSync(file, "utf-8")
            const relativeFile = path.relative(repoRoot, file)
            const usages = translationKeyUsages(source)
            const namespaces = translationNamespaceUsages(source)

            if (usages.length > 0) usagesByFile[relativeFile] = usages
            if (namespaces.length > 0) namespacesByFile[relativeFile] = namespaces
            collectKeyEvidence(source, evidence)
        }
    }

    return {usagesByFile, namespacesByFile, evidence}
}

/** Adds `result.undefinedKeys` for every literal translation key the scanned source uses that none of `definedKeys` contains. */
function checkUsedKeys(result, label, {usagesByFile, namespacesByFile}, definedKeys) {
    const findings = undefinedKeyUsages(usagesByFile, definedKeys)
    result.undefinedKeys.push(...findings)
    for (const {file, line, key} of findings) {
        annotate("error", `[${label}] Translation key "${key}" is used in ${file}:${line} but defined in no en.json, so it renders as its raw id - add it to en.json (or reuse an existing key) and run \`npm run translations:generate\``, {file, line})
    }

    const namespaceFindings = undefinedNamespaceUsages(namespacesByFile, definedKeys)
    result.undefinedKeys.push(...namespaceFindings.map(({file, line, namespace}) => ({file, line, key: `${namespace}.*`})))
    for (const {file, line, namespace} of namespaceFindings) {
        annotate("error", `[${label}] Translation keys under "${namespace}." are built at runtime in ${file}:${line}, but no en.json defines that namespace, so every one of them renders as its raw id - restore the namespace in en.json and run \`npm run translations:generate\``, {file, line})
    }
}

/** Adds `result.unusedKeys` for every leaf of `leaves` the evidence cannot reach. */
function checkUnusedKeys(result, label, leaves, evidence, fixPath) {
    const unused = unusedDefinedKeys(leaves, evidence)
    result.unusedKeys.push(...unused)
    for (const key of unused) {
        annotate("error", `[${label}] Translation key "${key}" is defined in ${fixPath} but nothing in the source can reach it - delete it from en.json, every locale and fingerprints.json; if a value chosen at runtime selects it, declare it where that value comes from with an \`i18n-keys: ${key}\` comment`)
    }
}

/** The review list behind `--unused-candidates`: leaves with no literal t() call, grouped by their first segment. */
function printUnusedCandidates(label, leaves, evidence) {
    const candidates = unusedDefinedKeys(leaves, {...evidence, strings: new Set(), patterns: []})
    const groups = [...Map.groupBy(candidates, key => key.split(".")[0])].sort((a, b) => b[1].length - a[1].length)

    console.log(
        `\n[${label}] ${candidates.length} of ${leaves.length} keys have no literal t() call; ` +
        "they reach t() through data, a template or a value chosen at runtime, or are dead:",
    )
    for (const [group, keys] of groups) {
        console.log(`  ${group} (${keys.length}): ${keys.map(key => key.slice(group.length + 1) || "<leaf>").join(", ")}`)
    }
}

/** The design-system `*.locale.ts` files, which carry their own `en` block. OSS-only: EE has none. */
function checkDesignSystem(result) {
    const localeFiles = designSystemLocaleFiles
    if (localeFiles.length === 0) return

    for (const {file, lang, key} of untranslatedLocaleEntries(localeFiles)) {
        (result.untranslated[lang] ??= []).push(`${file}: ${key}`)
        annotate("error", `[OSS] Design-system string "${key}" in ${file} still holds the English text in "${lang}" - blank the value and run \`npm run translations:generate\` in ui/`)
    }

    const fingerprintsFile = path.join(here, "fingerprints-design-system.json")
    if (!fs.existsSync(fingerprintsFile)) return

    const stale = staleLocaleEntries(localeFiles, fingerprintsFile)
    if (stale.length === 0) return

    result.stale.push(...stale.map(({file, key}) => `${file}: ${key}`))
    for (const {file, key} of stale) {
        annotate("error", `[OSS] Design-system string "${key}" in ${file} has no up-to-date translation - it is either new, or its English source changed after it was translated. Run \`npm run translations:generate\` in ui/`)
    }
}

/** OSS languages must match OSS's own en.json; a failure is fixed in kestra-io/kestra, not in EE. */
function checkOss() {
    const result = {missing: {}, duplicates: [], placeholders: {}, stale: [], untranslated: {}, undefinedKeys: [], unusedKeys: []}

    if (!fs.existsSync(ossTranslationsDir)) {
        annotate("warning", `OSS translations directory not found at ${ossTranslationsDir} - skipping OSS check.`)
        return result
    }

    checkPlaceholders(result, "OSS", ossTranslationsDir, "kestra-io/kestra's ui/src/translations/{lang}.json")
    checkUntranslated(result, "OSS", ossTranslationsDir, "kestra-io/kestra's ui/src/translations/{lang}.json")
    checkDesignSystem(result)
    checkStaleJson(result, "OSS", ossTranslationsDir, path.join(here, "fingerprints.json"), "run `npm run translations:generate` in kestra-io/kestra's ui/ and commit the result")
    const scan = scanSources(ossRoot, ossSourceRoots)
    checkUsedKeys(result, "OSS", scan, ossDefinedKeys())

    const ossEn = readLanguage(ossTranslationsDir, "en")
    const ossEnKeys = leafKeys(ossEn)

    // EE code renders OSS keys too, so "unused" can only be decided with both trees in view - and, as for
    // the EE keys below, only with the full OSS source tree (`ui/packages` included), which the release
    // branch without a design system does not have.
    if (fs.existsSync(eeSourceRoots[0]) && hasOssSources()) {
        scanSources(eeRoot, eeSourceRoots, scan.evidence)
        if (unusedCandidatesOnly) printUnusedCandidates("OSS", ossEnKeys, scan.evidence)
        else checkUnusedKeys(result, "OSS", ossEnKeys, scan.evidence, "kestra-io/kestra's ui/src/translations/en.json")
    } else {
        annotate("warning", `EE sources not found at ${eeSourceRoots[0]}, or OSS checked out without ui/packages - skipping the unused-key check for OSS keys, which EE code may render.`)
    }
    if (unusedCandidatesOnly) return result

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

/** How an EE key collides with an OSS one, since EE's locales are merged over OSS's at runtime. */
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

/** EE languages must match EE's own en.json, and no EE key may shadow one OSS defines. */
function checkEe() {
    const result = {missing: {}, duplicates: [], placeholders: {}, stale: [], untranslated: {}, undefinedKeys: [], unusedKeys: []}
    checkPlaceholders(result, "EE", eeTranslationsDir, "ui-ee/src/translations/ee_translations/{lang}.json")
    checkUntranslated(result, "EE", eeTranslationsDir, "ui-ee/src/translations/ee_translations/{lang}.json")
    checkStaleJson(result, "EE", eeTranslationsDir, path.join(eeRoot, "ui-ee/scripts/translations/fingerprints.json"), "run `npm run translations:generate` in ui-ee/ and commit the result")
    const eeEn = readLanguage(eeTranslationsDir, "en")
    const eeEnKeys = leafKeys(eeEn)

    // EE code reaches OSS and design-system keys too: its locale files are merged over OSS's.
    const definedKeys = ossDefinedKeys()
    for (const key of allKeys(eeEn)) definedKeys.add(key)
    for (const {dir, prefix} of eeTenantTypeTranslations()) {
        definedKeys.add(prefix)
        for (const key of allKeys(readLanguage(dir, "en"))) definedKeys.add(`${prefix}.${key}`)
    }
    const scan = scanSources(eeRoot, eeSourceRoots)
    checkUsedKeys(result, "EE", scan, definedKeys)

    // OSS code renders EE keys as well when EE data flows through it (`empty.${type}` for an EE-only type).
    if (hasOssSources()) {
        scanSources(ossRoot, ossSourceRoots, scan.evidence)
        if (unusedCandidatesOnly) {
            printUnusedCandidates("EE", eeEnKeys, scan.evidence)
            return result
        }
        checkUnusedKeys(result, "EE", eeEnKeys, scan.evidence, "ui-ee/src/translations/ee_translations/en.json")
        for (const {dir, prefix} of eeTenantTypeTranslations()) {
            const leaves = leafKeys(readLanguage(dir, "en")).map(key => `${prefix}.${key}`)
            checkUnusedKeys(result, "EE", leaves, scan.evidence, path.relative(eeRoot, path.join(dir, "en.json")))
        }
    } else {
        annotate("warning", `OSS sources not found at ${ossSourceRoots[0]} - skipping the unused-key check for EE keys, which OSS code may render.`)
        if (unusedCandidatesOnly) return result
    }

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
        || result.undefinedKeys.length > 0
        || result.unusedKeys.length > 0
}

const report = {scope, missing: {}, duplicates: [], placeholders: {}, stale: [], untranslated: {}, undefinedKeys: [], unusedKeys: []}
let hasFailure = false

function mergeIntoReport(result) {
    hasFailure = hasFailure || hasIssues(result)
    Object.assign(report.missing, result.missing)
    Object.assign(report.placeholders, result.placeholders)
    report.duplicates.push(...result.duplicates)
    report.stale.push(...result.stale)
    Object.assign(report.untranslated, result.untranslated)
    report.undefinedKeys.push(...result.undefinedKeys)
    report.unusedKeys.push(...result.unusedKeys)
}

if (scope === "oss" || scope === "all") mergeIntoReport(checkOss())
if (scope === "ee" || scope === "all") mergeIntoReport(checkEe())

if (unusedCandidatesOnly) process.exit(0)

if (reportPath) {
    fs.mkdirSync(path.dirname(path.resolve(reportPath)), {recursive: true})
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2))
}

if (hasFailure) {
    console.error("\nTranslation check failed - see ::error:: annotations above.")
    process.exit(1)
}

console.log(`Translation check passed (scope: ${scope}).`)
