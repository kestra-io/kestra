/**
 * Generates UI translations from en.json using OpenAI.
 *
 * Run from the repository root so the relative paths below resolve correctly:
 *   OPENAI_API_KEY=... node --experimental-strip-types ui/scripts/generate_translations.ts [true|false]
 *
 * The single positional argument mirrors `retranslate_modified_keys`: pass "true"
 * to re-translate keys that already have a non-empty translation.
 *
 * Requires the `openai` package and Node 22+ (for native TypeScript type stripping).
 */
import {execFileSync} from "node:child_process"
import {readFileSync, writeFileSync} from "node:fs"
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

// Default to 'false' if no argument is provided
const boolFromCi = process.argv[2]?.toLowerCase() === "true"

for (const [languageCode, targetLanguage] of LANGUAGES) {
    await main(languageCode, targetLanguage, "ui/src/translations/en.json", boolFromCi)
}
