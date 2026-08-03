/**
 * Generates UI translations from en.json using Gemini.
 *
 * Two sources are translated:
 *   1. `ui/src/translations/en.json` -> one JSON file per language (de.json, fr.json, ...).
 *   2. The design-system `*.locale.ts` files (ui/packages/design-system/.../  *.locale.ts),
 *      each of which holds every language in a single `export default { en: {...}, de: {...}, ... }`.
 *
 * Runs from anywhere — from `ui/` via `npm run translations:generate`, or from the repository root:
 *   GEMINI_API_KEY=... node --experimental-strip-types ui/scripts/generate_translations.ts [true|false]
 *
 * The single positional argument mirrors `retranslate_modified_keys`: pass "true"
 * to re-translate keys that already have a non-empty translation.
 *
 * Requires the `@google/genai` package and Node 22+ (for native TypeScript type stripping and fs.globSync).
 */
import {execFileSync} from "node:child_process"
import {globSync, readFileSync, writeFileSync} from "node:fs"
import {dirname, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {GoogleGenAI} from "@google/genai"

// Every path in this file — and the `git log`/`git show` pathspecs — is written relative to the
// repository root, so the script anchors itself there rather than depending on where it was
// launched from. Without this, running it from `ui/` would look for `ui/ui/src/translations`.
process.chdir(resolve(dirname(fileURLToPath(import.meta.url)), "../.."))

const MODEL = "gemini-2.5-flash"
const client = new GoogleGenAI({apiKey: process.env.GEMINI_API_KEY})

type NestedValue = string | NestedValue[] | NestedDict;
type NestedDict = {[key: string]: NestedValue};
type FlatDict = {[key: string]: string};

// How many translation requests may be in flight at once, across every language and every file.
// Translating one key at a time made a full backlog take far longer than the workflow's job
// timeout, so the run was cancelled before committing anything and the backlog only ever grew.
// Raise it for a faster local run; lower it if the provider starts rate-limiting.
const CONCURRENCY = Math.max(1, Number(process.env.TRANSLATION_CONCURRENCY ?? 10))

/**
 * Returns a gate that admits at most `limit` concurrent tasks and queues the rest.
 *
 * The gate wraps the single point of network I/O rather than each loop, so callers are free to
 * schedule as many translations as they like — by language, by key, by locale file — while the
 * number of simultaneous requests stays bounded by one shared budget.
 */
function createGate(limit: number): <T>(task: () => Promise<T>) => Promise<T> {
    let active = 0
    const waiting: (() => void)[] = []

    return async function run<T>(task: () => Promise<T>): Promise<T> {
        if (active >= limit) {
            await new Promise<void>((resolve) => waiting.push(resolve))
        }
        active++
        try {
            return await task()
        } finally {
            active--
            waiting.shift()?.()
        }
    }
}

const withRequestSlot = createGate(CONCURRENCY)

async function translateText(text: string, targetLanguage: string): Promise<string> {
    const prompt = `Translate the text provided after "----------" into ${targetLanguage} for use in Kestra’s orchestration UI. Follow these guidelines:
        - Output Only the Translation: Provide only the translated text, with no additional commentary or explanation.
        - Maintain Technical Accuracy: Use correct translations for technical terms (avoid literal translations that change the meaning).
        - Reserved English Terms (Do Not Translate): Keep the following terms in English (adjusting capitalization or plural forms as needed): kv store, namespace, flow, subflow, task, log, blueprint, id, trigger, label, key, value, input, output, port, worker, backfill, healthcheck, min, max. For example, in German, "log" must remain "Log" in phrases: translate "Log level" as "Log-Ebene" (not "Protokoll-Ebene"), and "Task logs" stays "Task Logs" (not "Aufgabenprotokolle"). Important: do not alter "flow" or "namespace" at all – keep them exactly as "flow" and "namespace."
        - UI Terminology Consistency: Ensure the translation sounds natural for a software interface. Avoid overly formal or word-for-word translations that feel unnatural in a UI. Use terminology that users expect in the target language. For example, in German translations:
          - State → Zustand (not "Staat")
          - Execution → Ausführung (not "Hinrichtung")
          - Theme → Modus (not "Thema")
          - Concurrency → Nebenläufigkeit (not "Konkurrenz")
          - Tenant (in multi-tenant context) → Mandant (not "Mieter")
          - Expand (UI control) → Ausklappen (not "Erweitern")
          - Tab (interface element) → Registerkarte (not "Reiter")
          - Creation → Erstellung (not "Schöpfung")
          Apply similar context-appropriate translations in other languages to avoid false friends or misleading terms.
        - State Labels in English: Keep status labels that are in all caps (e.g. WARNING, FAILED, SUCCESS, PAUSED, RUNNING) in English and in their original uppercase format.
        - Preserve Variables: Do not translate or change any placeholders enclosed in double curly braces (e.g. \`{{label}}\`, \`{{key}}\`). Leave them exactly as they are. For example, "System {{label}}" should remain "System {{label}}" in the translated text (do not translate "label" or remove the braces).

        If the loaded dictionary has no key-value pairs to translate, it means we're adding a new language, and we need to translate all the keys from English to ${targetLanguage}.

        Here is the text to translate:
        ----------
        ${text}
        `

    try {
        const response = await withRequestSlot(() => client.models.generateContent({
            model: MODEL,
            contents: prompt,
            config: {
                systemInstruction: `You are a software engineer translating textual UI elements into ${targetLanguage} while keeping technical terms in English.`,
                temperature: 0.1,
                // Translating a short UI string needs no deliberation, and the thinking pass is what
                // made each call slow: median latency measured over the prompts this script actually
                // sends drops from ~2.5s to ~0.5s with it switched off, with no loss of quality on
                // placeholder or reserved-term handling.
                thinkingConfig: {thinkingBudget: 0},
            },
        }))
        return (response.text ?? "").trim()
    } catch (e) {
        console.log(`Error during translation: ${e}`)
        return text // Return original if translation fails
    }
}

function unflattenDict(d: FlatDict, sep = "|"): NestedDict {
    const result: NestedDict = {}
    for (const [k, v] of Object.entries(d)) {
        const keys = k.split(sep)
        let current = result
        for (const key of keys.slice(0, -1)) {
            if (typeof current[key] !== "object" || current[key] === null) {
                current[key] = {}
            }
            current = current[key] as NestedDict
        }
        current[keys[keys.length - 1]] = v
    }
    // Arrays were flattened with numeric string keys ("0", "1", ...); rebuild them
    // so the original list structure is preserved instead of becoming an object.
    return arrayifyNumericKeys(result) as NestedDict
}

// Recursively convert objects whose keys are exactly the consecutive indices
// 0..n-1 back into arrays. This reverses how flattenDict() walks arrays via their
// numeric keys, which would otherwise round-trip an array into a numeric-keyed object.
function arrayifyNumericKeys(value: NestedValue): NestedValue {
    if (value === null || typeof value !== "object") {
        return value
    }
    if (Array.isArray(value)) {
        return value.map(arrayifyNumericKeys)
    }
    const keys = Object.keys(value)
    const processed: NestedDict = {}
    for (const key of keys) {
        processed[key] = arrayifyNumericKeys(value[key])
    }
    const isArray = keys.length > 0
        && keys.every((k) => /^\d+$/.test(k))
        && keys.map(Number).sort((a, b) => a - b).every((n, i) => n === i)
    if (isArray) {
        return keys
            .map(Number)
            .sort((a, b) => a - b)
            .map((n) => processed[String(n)])
    }
    return processed
}

function flattenDict(d: NestedValue, parentKey = "", sep = "|"): FlatDict {
    const items: FlatDict = {}
    for (const [k, v] of Object.entries(d)) {
        const newKey = parentKey ? `${parentKey}${sep}${k}` : k
        if (v !== null && typeof v === "object") {
            Object.assign(items, flattenDict(v, newKey, sep))
        } else {
            items[newKey] = v
        }
    }
    return items
}

function loadEnChangesFromLastCommits(inputFile: string): NestedDict {
    // Fetch all remote branches (including fork commits merged into remotes)
    execFileSync("git", ["fetch", "--all"], {stdio: "ignore"})

    // Get the two most recent commits that modified the input_file.
    const commits = (execFileSync("git", ["log", "-n", "2", "--format=%H", "--", inputFile], {encoding: "utf-8"}) as string)
        .split("\n")
        .filter((line) => line.length > 0)
    if (commits.length < 2) {
        return {}
    }

    // Compare the current working file with the version from the previous commit.
    const previousCommit = commits[1]
    try {
        const previousVersion = execFileSync("git", ["show", `${previousCommit}:${inputFile}`], {encoding: "utf-8"})
        return JSON.parse(previousVersion)
    } catch {
        return {}
    }
}

function loadEnDict(filePath: string): NestedDict {
    return JSON.parse(readFileSync(filePath, "utf-8"))
}

function detectChanges(currentDict: NestedDict, previousDict: NestedDict): Set<string> {
    const addedKeys: string[] = []
    const changedKeys: string[] = []

    const currentFlat = flattenDict(currentDict)
    const previousFlat = flattenDict(previousDict)

    for (const key of Object.keys(currentFlat)) {
        if (!(key in previousFlat)) {
            addedKeys.push(key)
        } else if (currentFlat[key] !== previousFlat[key]) {
            changedKeys.push(key)
        }
    }

    return new Set([...addedKeys, ...changedKeys])
}

// The changed-key set depends only on en.json, yet it used to be recomputed for every language —
// running `git fetch --all` and re-parsing two revisions of the file twelve times over. Memoised
// per input file so that work happens once. Populated synchronously before the first await, so
// concurrent language runs cannot race on it.
const changedEnKeysByFile = new Map<string, FlatDict>()

function getKeysToTranslate(filePath = "ui/src/translations/en.json"): FlatDict {
    const memoised = changedEnKeysByFile.get(filePath)
    if (memoised) {
        return memoised
    }

    const currentEnDict = loadEnDict(filePath)
    const previousEnDict = loadEnChangesFromLastCommits(filePath)

    const keysToTranslate = detectChanges(currentEnDict, previousEnDict)
    const enFlat = flattenDict(currentEnDict)
    const result: FlatDict = {}
    for (const k of keysToTranslate) {
        result[k] = enFlat[k]
    }
    changedEnKeysByFile.set(filePath, result)
    return result
}

function removeEnPrefix(dictionary: FlatDict, prefix = "en|"): FlatDict {
    const result: FlatDict = {}
    for (const [k, v] of Object.entries(dictionary)) {
        if (k.startsWith(prefix)) {
            result[k.slice(prefix.length)] = v
        }
    }
    return result
}

async function main(
    languageCode: string,
    targetLanguage: string,
    inputFile = "ui/src/translations/en.json",
    retranslateModifiedKeys = false,
): Promise<void> {
    const targetDict = JSON.parse(readFileSync(`ui/src/translations/${languageCode}.json`, "utf-8"))[languageCode] as NestedDict

    // The full set of (nested) keys defined in en.json, flattened and stripped of the "en|" prefix.
    const enFlat = removeEnPrefix(flattenDict(loadEnDict(inputFile)))

    const targetFlat = flattenDict(targetDict)

    // Keys to translate come from two sources:
    //  1. Keys whose English source changed in the latest commit (content updates).
    //  2. Keys present in en.json but missing (or empty) in the target language file.
    const toTranslate: FlatDict = removeEnPrefix(getKeysToTranslate(inputFile))
    for (const [k, v] of Object.entries(enFlat)) {
        if (!(k in targetFlat) || !targetFlat[k]) {
            toTranslate[k] = v
        }
    }

    const translatedFlatDict: FlatDict = {}

    // Only re-translate if the key is not already in the target dict or is empty
    const pending = Object.entries(toTranslate).filter(([k]) => {
        // If we already have a non-empty translation, skip unless forced to re-translate
        if (k in targetFlat && targetFlat[k] && !retranslateModifiedKeys) {
            console.log(`Skipping re-translation for '${k}' since a translation already exists.`)
            return false
        }
        return true
    })

    // Requested together rather than one after another; the gate around the API call caps how many
    // are actually in flight. Writing into a dictionary keyed by translation key means completion
    // order does not matter, and the output ordering is rebuilt from en.json just below.
    await Promise.all(pending.map(async ([k, v]) => {
        const newTranslation = await translateText(v, targetLanguage)
        translatedFlatDict[k] = newTranslation
        console.log(`Translating ${k}:${v} to ${targetLanguage} -> '${newTranslation}'.`)
    }))

    Object.assign(targetFlat, translatedFlatDict)

    // Rebuild the language dict in en.json key order so the output mirrors the
    // reference ordering. This keeps regeneration from reordering existing
    // key/value pairs — which would otherwise open PRs that only rearrange keys —
    // and, by iterating enFlat, also drops any key no longer present in en.json.
    const orderedTargetFlat: FlatDict = {}
    for (const k of Object.keys(enFlat)) {
        if (k in targetFlat) {
            orderedTargetFlat[k] = targetFlat[k]
        }
    }

    const updatedTargetDict = unflattenDict(orderedTargetFlat)

    const output = {[languageCode]: updatedTargetDict}
    writeFileSync(`ui/src/translations/${languageCode}.json`, JSON.stringify(output, null, 2))
}

// ---------------------------------------------------------------------------
// Design-system `*.locale.ts` files
//
// Unlike the per-language JSON files, each `*.locale.ts` file bundles every
// language in a single default export:
//
//   export default {
//       en: { ... },
//       de: { ... },
//       ...
//   }
//
// These files contain only string values and nested objects (no imports, types
// or function calls), which lets us evaluate them as plain object literals and
// re-serialise them back to TypeScript after filling in the translations.
// ---------------------------------------------------------------------------

// Evaluate the body of a `*.locale.ts` default export into a plain object.
// The files are pure data literals, so this is safe (and far simpler than parsing TS).
function evalLocaleModule(source: string): {[lang: string]: NestedDict} {
    const body = source
        .replace(/export\s+default\s*/, "")
        .replace(/;?\s*$/, "")
    return new Function(`return (${body})`)() as {[lang: string]: NestedDict}
}

// Serialise a value back to TypeScript source, matching the existing 4-space
// indentation and trailing-comma style. Keys are always quoted: many of them have to be
// (`"customize tooltip"` contains a space), so quoting only the ones that strictly need it left
// each file inconsistent with itself and made the serialiser rewrite whichever keys happened to be
// stored the other way. Quoting everything is one rule, applied uniformly, and keeps regeneration
// a no-op when nothing was translated.
function serializeLocaleValue(value: NestedValue, indent: number): string {
    if (value === null || typeof value !== "object") {
        return JSON.stringify(value)
    }

    const pad = "    ".repeat(indent)
    const padInner = "    ".repeat(indent + 1)

    if (Array.isArray(value)) {
        if (value.length === 0) {
            return "[]"
        }
        const items = value.map((v) => `${padInner}${serializeLocaleValue(v, indent + 1)},`)
        return `[\n${items.join("\n")}\n${pad}]`
    }

    const entries = Object.entries(value)
    if (entries.length === 0) {
        return "{}"
    }

    const lines = entries.map(([k, v]) =>
        `${padInner}${JSON.stringify(k)}: ${serializeLocaleValue(v, indent + 1)},`)
    return `{\n${lines.join("\n")}\n${pad}}`
}

function serializeLocaleModule(data: {[lang: string]: NestedDict}): string {
    return `export default ${serializeLocaleValue(data, 0)}\n`
}

// Load the `en` block of a `*.locale.ts` file from the previous commit so we can
// detect which English source strings changed (mirrors loadEnChangesFromLastCommits).
function loadLocaleEnFromLastCommits(filePath: string): NestedDict {
    const commits = (execFileSync("git", ["log", "-n", "2", "--format=%H", "--", filePath], {encoding: "utf-8"}) as string)
        .split("\n")
        .filter((line) => line.length > 0)
    if (commits.length < 2) {
        return {}
    }

    const previousCommit = commits[1]
    try {
        const previousVersion = execFileSync("git", ["show", `${previousCommit}:${filePath}`], {encoding: "utf-8"}) as string
        return evalLocaleModule(previousVersion).en ?? {}
    } catch {
        return {}
    }
}

// Translate the missing/changed keys of a single `*.locale.ts` file in place,
// using its own `en` block as the source of truth for every other language.
async function translateLocaleFile(filePath: string, retranslateModifiedKeys: boolean): Promise<void> {
    const data = evalLocaleModule(readFileSync(filePath, "utf-8"))
    if (!data.en) {
        console.log(`Skipping ${filePath}: no 'en' base translations found.`)
        return
    }

    const enFlat = flattenDict(data.en)

    // Keys whose English source changed since the previous commit (used only when forcing re-translation).
    const changedEnKeys = detectChanges(data.en, loadLocaleEnFromLastCommits(filePath))

    // Keep any unknown languages already present in the file, and add any shipped language missing from it.
    const codes = Object.keys(data).filter((code) => code !== "en")
    for (const [code] of LANGUAGES) {
        if (!codes.includes(code)) {
            codes.push(code)
        }
    }

    const translatedByCode = await Promise.all(codes.map(async (code): Promise<readonly [string, NestedDict]> => {
        const targetLanguage = LANGUAGE_BY_CODE[code]
        if (!targetLanguage) {
            // Language not in our translation list: keep whatever is already there.
            return [code, data[code]] as const
        }

        const targetFlat = flattenDict(data[code] ?? {})

        // Keys to translate: changed English source, plus keys missing or empty in the target language.
        const toTranslate: FlatDict = {}
        for (const k of changedEnKeys) {
            if (k in enFlat) {
                toTranslate[k] = enFlat[k]
            }
        }
        for (const [k, v] of Object.entries(enFlat)) {
            if (!(k in targetFlat) || !targetFlat[k]) {
                toTranslate[k] = v
            }
        }

        const pending = Object.entries(toTranslate).filter(([k]) => {
            // If we already have a non-empty translation, skip unless forced to re-translate.
            if (k in targetFlat && targetFlat[k] && !retranslateModifiedKeys) {
                console.log(`[${filePath}] Skipping re-translation for '${k}' since a translation already exists.`)
                return false
            }
            return true
        })

        const translatedFlatDict: FlatDict = {}
        await Promise.all(pending.map(async ([k, v]) => {
            const newTranslation = await translateText(v, targetLanguage)
            translatedFlatDict[k] = newTranslation
            console.log(`[${filePath}] Translating ${k}:${v} to ${targetLanguage} -> '${newTranslation}'.`)
        }))

        Object.assign(targetFlat, translatedFlatDict)

        // Rebuild the target dict in the same key order as `en`, dropping keys no longer present in `en`.
        const prunedTargetFlat: FlatDict = {}
        for (const k of Object.keys(enFlat)) {
            if (k in targetFlat) {
                prunedTargetFlat[k] = targetFlat[k]
            }
        }
        return [code, unflattenDict(prunedTargetFlat)] as const
    }))

    // Assembled after the fact in a fixed order — `en` first, then `codes` — rather than as each
    // language finishes. The serialised output preserves key order, so letting completion order
    // decide it would rewrite the whole file on every run.
    const result: {[lang: string]: NestedDict} = {en: data.en}
    for (const [code, dict] of translatedByCode) {
        result[code] = dict
    }

    writeFileSync(filePath, serializeLocaleModule(result))
}

const LANGUAGES: ReadonlyArray<readonly [string, string]> = [
    ["de", "German"],
    ["es", "Spanish"],
    ["fr", "French"],
    ["hi", "Hindi"],
    ["it", "Italian"],
    ["ja", "Japanese"],
    ["ko", "Korean"],
    ["pl", "Polish"],
    ["pt", "Portuguese"],
    ["pt_BR", "Portuguese (Brazil)"],
    ["ru", "Russian"],
    ["zh_CN", "Simplified Chinese (Mandarin)"],
]

const LANGUAGE_BY_CODE: {[code: string]: string} = Object.fromEntries(LANGUAGES)

// Default to 'false' if no argument is provided
const boolFromCi = process.argv[2]?.toLowerCase() === "true"

// Both phases run their units concurrently. Each language owns exactly one output file and each
// locale file is independent, so nothing is shared but the request gate, which bounds how many
// translations are in flight at once. Phase 1 is awaited before phase 2 only to keep the log
// readable; the two are otherwise independent.

// 1. Translate the per-language JSON files from en.json.
await Promise.all(LANGUAGES.map(([languageCode, targetLanguage]) =>
    main(languageCode, targetLanguage, "ui/src/translations/en.json", boolFromCi)))

// 2. Translate the design-system `*.locale.ts` files (each holds every language in one file).
await Promise.all(globSync("ui/packages/design-system/**/*.locale.ts")
    .map((localeFile) => translateLocaleFile(localeFile, boolFromCi)))
