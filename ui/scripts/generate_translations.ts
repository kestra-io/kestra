/**
 * Generates UI translations from en.json using OpenAI.
 *
 * Two sources are translated:
 *   1. `ui/src/translations/en.json` -> one JSON file per language (de.json, fr.json, ...).
 *   2. The design-system `*.locale.ts` files (ui/packages/design-system/.../  *.locale.ts),
 *      each of which holds every language in a single `export default { en: {...}, de: {...}, ... }`.
 *
 * Run from the repository root so the relative paths below resolve correctly:
 *   OPENAI_API_KEY=... node --experimental-strip-types ui/scripts/generate_translations.ts [true|false]
 *
 * The single positional argument mirrors `retranslate_modified_keys`: pass "true"
 * to re-translate keys that already have a non-empty translation.
 *
 * Requires the `openai` package and Node 22+ (for native TypeScript type stripping and fs.globSync).
 */
import {execFileSync} from "node:child_process"
import {globSync, readFileSync, writeFileSync} from "node:fs"
import OpenAI from "openai"

const client = new OpenAI()

type NestedDict = {[key: string]: string | NestedDict};
type FlatDict = {[key: string]: string};

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
        const response = await client.chat.completions.create({
            model: "gpt-4o",
            messages: [
                {
                    role: "system",
                    content: `You are a software engineer translating textual UI elements into ${targetLanguage} while keeping technical terms in English.`,
                },
                {
                    role: "user",
                    content: prompt,
                },
            ],
            temperature: 0.1,
        })
        return (response.choices[0].message.content ?? "").trim()
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
    return result
}

function flattenDict(d: NestedDict, parentKey = "", sep = "|"): FlatDict {
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

function getKeysToTranslate(filePath = "ui/src/translations/en.json"): FlatDict {
    const currentEnDict = loadEnDict(filePath)
    const previousEnDict = loadEnChangesFromLastCommits(filePath)

    const keysToTranslate = detectChanges(currentEnDict, previousEnDict)
    const enFlat = flattenDict(currentEnDict)
    const result: FlatDict = {}
    for (const k of keysToTranslate) {
        result[k] = enFlat[k]
    }
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

// Recursively sort object keys to mirror Python's json.dump(sort_keys=True).
function sortKeysRecursively(value: string | NestedDict): string | NestedDict {
    if (value === null || typeof value !== "object") {
        return value
    }
    const sorted: NestedDict = {}
    for (const key of Object.keys(value).sort()) {
        sorted[key] = sortKeysRecursively(value[key])
    }
    return sorted
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
    for (const [k, v] of Object.entries(toTranslate)) {
        // If we already have a non-empty translation, skip unless forced to re-translate
        if (k in targetFlat && targetFlat[k] && !retranslateModifiedKeys) {
            console.log(`Skipping re-translation for '${k}' since a translation already exists.`)
            continue
        }
        const newTranslation = await translateText(v, targetLanguage)
        translatedFlatDict[k] = newTranslation
        console.log(`Translating ${k}:${v} to ${targetLanguage} -> '${newTranslation}'.`)
    }

    Object.assign(targetFlat, translatedFlatDict)

    // Drop any key that no longer exists in en.json (e.g. removed from the source language).
    const prunedTargetFlat: FlatDict = {}
    for (const [k, v] of Object.entries(targetFlat)) {
        if (k in enFlat) {
            prunedTargetFlat[k] = v
        }
    }

    const updatedTargetDict = unflattenDict(prunedTargetFlat)

    // Sort keys to keep output stable
    const output = sortKeysRecursively({[languageCode]: updatedTargetDict})
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

const IDENTIFIER = /^[A-Za-z_$][A-Za-z0-9_$]*$/

// Evaluate the body of a `*.locale.ts` default export into a plain object.
// The files are pure data literals, so this is safe (and far simpler than parsing TS).
function evalLocaleModule(source: string): {[lang: string]: NestedDict} {
    const body = source
        .replace(/export\s+default\s*/, "")
        .replace(/;?\s*$/, "")
    return new Function(`return (${body})`)() as {[lang: string]: NestedDict}
}

// Serialise a value back to TypeScript source, matching the existing 4-space
// indentation, trailing commas, and unquoted-identifier-keys style.
function serializeLocaleValue(value: string | NestedDict, indent: number): string {
    if (value === null || typeof value !== "object") {
        return JSON.stringify(value)
    }

    const pad = "    ".repeat(indent)
    const padInner = "    ".repeat(indent + 1)
    const entries = Object.entries(value)
    if (entries.length === 0) {
        return "{}"
    }

    const lines = entries.map(([k, v]) => {
        const key = IDENTIFIER.test(k) ? k : JSON.stringify(k)
        return `${padInner}${key}: ${serializeLocaleValue(v, indent + 1)},`
    })
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

    // Preserve `en` first and keep any unknown languages already present in the file untouched.
    const result: {[lang: string]: NestedDict} = {en: data.en}
    const codes = Object.keys(data).filter((code) => code !== "en")
    for (const [code] of LANGUAGES) {
        if (!codes.includes(code)) {
            codes.push(code)
        }
    }

    for (const code of codes) {
        const targetLanguage = LANGUAGE_BY_CODE[code]
        if (!targetLanguage) {
            // Language not in our translation list: keep whatever is already there.
            result[code] = data[code]
            continue
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

        const translatedFlatDict: FlatDict = {}
        for (const [k, v] of Object.entries(toTranslate)) {
            // If we already have a non-empty translation, skip unless forced to re-translate.
            if (k in targetFlat && targetFlat[k] && !retranslateModifiedKeys) {
                console.log(`[${filePath}] Skipping re-translation for '${k}' since a translation already exists.`)
                continue
            }
            const newTranslation = await translateText(v, targetLanguage)
            translatedFlatDict[k] = newTranslation
            console.log(`[${filePath}] Translating ${k}:${v} to ${targetLanguage} -> '${newTranslation}'.`)
        }

        Object.assign(targetFlat, translatedFlatDict)

        // Rebuild the target dict in the same key order as `en`, dropping keys no longer present in `en`.
        const prunedTargetFlat: FlatDict = {}
        for (const k of Object.keys(enFlat)) {
            if (k in targetFlat) {
                prunedTargetFlat[k] = targetFlat[k]
            }
        }
        result[code] = unflattenDict(prunedTargetFlat)
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

// 1. Translate the per-language JSON files from en.json.
for (const [languageCode, targetLanguage] of LANGUAGES) {
    await main(languageCode, targetLanguage, "ui/src/translations/en.json", boolFromCi)
}

// 2. Translate the design-system `*.locale.ts` files (each holds every language in one file).
for (const localeFile of globSync("ui/packages/design-system/**/*.locale.ts")) {
    await translateLocaleFile(localeFile, boolFromCi)
}
