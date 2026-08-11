/**
 * Shared translation generator: fills the non-English UI translations from an English reference,
 * using Gemini.
 *
 * Shared by OSS (`kestra/ui`) and EE (`kestra-ee/ui-ee`) — each caller passes the directory that
 * holds its own language JSON files, which is what repoints the generator at EE. This mirrors how
 * `./compareTranslations.ts` is shared with `ui-ee/scripts/translations/check.ts`.
 *
 * Two sources can be translated:
 *   1. A directory of per-language JSON files (`en.json` -> `de.json`, `fr.json`, ...) via
 *      {@link generateTranslations}. Both repos use this.
 *   2. The design-system `*.locale.ts` files, each of which holds every language in a single
 *      `export default {en: {...}, de: {...}, ...}`, via {@link translateLocaleFiles}. OSS only.
 *
 * The Gemini client is injected rather than constructed here, so `@google/genai` resolves from the
 * *calling* repo's `node_modules`. Without that, running this from EE would need OSS's dependencies
 * installed as well, just to reach a module that lives in the OSS checkout.
 *
 * This module has no side effects beyond sizing the shared request gate: it never chdirs, never
 * reads `process.argv` and never writes a file unless a caller asks it to.
 */
import {readFileSync} from "node:fs"
import {dirname, relative, resolve} from "node:path"
import {writeIfChanged} from "./files.ts"
import {
    type Fingerprints,
    fingerprintOf,
    KEY_SEPARATOR,
    readFingerprints,
    writeFingerprints,
} from "./fingerprints.ts"

/**
 * The slice of `@google/genai`'s `GoogleGenAI` this generator actually uses.
 *
 * Typed structurally so the shared module carries no dependency on the SDK — see the note on
 * injection above.
 */
export interface TranslationClient {
    models: {
        generateContent(request: {
            model: string;
            contents: string;
            config?: {
                systemInstruction?: string;
                temperature?: number;
                thinkingConfig?: {thinkingBudget: number};
            };
        }): Promise<{text?: string}>;
    };
}

type NestedValue = string | NestedValue[] | NestedDict;
type NestedDict = {[key: string]: NestedValue};
type FlatDict = {[key: string]: string};

/** (language code, Gemini target language) pairs. English is the reference and is never translated. */
export const LANGUAGES: ReadonlyArray<readonly [string, string]> = [
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

const MODEL = "gemini-2.5-flash"


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
            await new Promise<void>((admit) => waiting.push(admit))
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

// Module-level so both phases of an OSS run draw from one budget rather than one each.
const withRequestSlot = createGate(CONCURRENCY)

/**
 * Translates one string, or returns `undefined` if the call failed.
 *
 * The failure is reported rather than papered over with the English text, so the caller can leave
 * that key's fingerprint alone — recording it would claim a translation exists and suppress every
 * future retry.
 */
async function translateText(client: TranslationClient, text: string, targetLanguage: string): Promise<string | undefined> {
    const prompt = `Translate the text provided after "----------" into ${targetLanguage} for use in Kestra’s orchestration UI. Follow these guidelines:
        - Output Only the Translation: Provide only the translated text, with no additional commentary or explanation.
        - Maintain Technical Accuracy: Use correct translations for technical terms (avoid literal translations that change the meaning).
        - Reserved English Terms (Do Not Translate): Keep the following terms in English (adjusting capitalization or plural forms as needed): kv store, namespace, tenant, flow, subflow, task, log, blueprint, id, trigger, label, key, value, input, output, port, worker, backfill, healthcheck, min, max. For example, in German, "log" must remain "Log" in phrases: translate "Log level" as "Log-Ebene" (not "Protokoll-Ebene"), and "Task logs" stays "Task Logs" (not "Aufgabenprotokolle"). Important: do not alter "flow", "namespace" or "tenant" at all – keep them exactly as "flow", "namespace" and "tenant". In German, "tenant" must stay "Tenant" (never "Mandant" or "Mieter").
        - UI Terminology Consistency: Ensure the translation sounds natural for a software interface. Avoid overly formal or word-for-word translations that feel unnatural in a UI. Use terminology that users expect in the target language. For example, in German translations:
          - State → Zustand (not "Staat")
          - Execution → Ausführung (not "Hinrichtung")
          - Theme → Modus (not "Thema")
          - Concurrency → Nebenläufigkeit (not "Konkurrenz")
          - Expand (UI control) → Ausklappen (not "Erweitern")
          - Tab (interface element) → Registerkarte (not "Reiter")
          - Creation → Erstellung (not "Schöpfung")
          Apply similar context-appropriate translations in other languages to avoid false friends or misleading terms.
        - State Labels in English: Keep status labels that are in all caps (e.g. WARNING, FAILED, SUCCESS, PAUSED, RUNNING) in English and in their original uppercase format.
        - Preserve Variables: Placeholders are enclosed in a SINGLE pair of curly braces (e.g. \`{label}\`, \`{key}\`). Copy them verbatim: do not translate the name inside the braces, do not rename it, do not add or remove braces, and never turn \`{label}\` into \`{{label}}\` — vue-i18n rejects double braces with a "Not allowed nest placeholder" compile error. For example, "System {label}" must stay "System {label}" in the translated text. Reproduce exactly the same set of placeholders as the source string — never invent a placeholder the source does not have, and never drop one it does.

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
        const translated = (response.text ?? "").trim()
        return translated.length ? translated : undefined
    } catch (e) {
        console.log(`Error during translation: ${e}`)
        return undefined
    }
}

/**
 * Flattens every leaf, not just strings — unlike `flattenStrings` in `./fingerprints.ts`.
 *
 * The two differ deliberately: this one round-trips through `unflattenDict` to rebuild a language
 * file, so a numeric or boolean leaf has to survive the trip rather than be dropped. Fingerprints
 * only ever describe translatable text, so they use the string-only variant. Both share
 * `KEY_SEPARATOR`, which is the part that must not drift.
 */
function flattenDict(d: NestedValue, parentKey = "", sep = KEY_SEPARATOR): FlatDict {
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

function unflattenDict(d: FlatDict, sep = KEY_SEPARATOR): NestedDict {
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

export interface GenerateTranslationsOptions {
    /** Gemini client, constructed by the caller so the SDK resolves from its own `node_modules`. */
    client: TranslationClient;
    /** Absolute path to the folder holding `en.json` and the locale files. */
    translationsDir: string;
    /**
     * Absolute path to the JSON file recording, per key, a hash of the English text the
     * translations were last generated from. Omit it and every key is treated as up to date
     * unless it is missing or empty in the target.
     */
    fingerprintsFile?: string;
    /** Re-translate every key, ignoring the fingerprints. The manual "start fresh" escape hatch. */
    force?: boolean;
    /** Language codes to fill (defaults to every shipped locale). */
    languages?: ReadonlyArray<readonly [string, string]>;
}

/**
 * Fills the per-language JSON files in `translationsDir` from its `en.json`.
 *
 * Each language file is rewritten in `en.json` key order, so regeneration never reorders existing
 * entries, and keys no longer present in English are dropped.
 */
export async function generateTranslations(options: GenerateTranslationsOptions): Promise<void> {
    const {client, translationsDir, fingerprintsFile, force = false, languages = LANGUAGES} = options

    const filePathFor = (code: string): string => resolve(translationsDir, `${code}.json`)
    const enFile = filePathFor("en")

    const enFlat = flattenDict(JSON.parse(readFileSync(enFile, "utf-8"))["en"] as NestedDict)
    const fingerprints = readFingerprints(fingerprintsFile)

    const translatableKeys = Object.keys(enFlat).filter((key) => typeof enFlat[key] === "string")
    const staleKeys = new Set(
        force
            ? translatableKeys
            : translatableKeys.filter((key) => fingerprints[key] !== fingerprintOf(enFlat[key])),
    )
    if (staleKeys.size) {
        console.log(`${staleKeys.size} key(s) whose English source changed since they were last translated.`)
    }

    // Keys that failed to translate in at least one language. Their fingerprint is deliberately
    // left untouched so the next run picks them up again instead of considering them settled.
    const failedKeys = new Set<string>()

    // Languages run concurrently: each owns exactly one output file, so nothing is shared but the
    // request gate, which bounds how many translations are in flight at once.
    await Promise.all(languages.map(async ([languageCode, targetLanguage]) => {
        const targetPath = filePathFor(languageCode)
        const targetFlat = flattenDict(JSON.parse(readFileSync(targetPath, "utf-8"))[languageCode] as NestedDict)

        // Only strings are sent to the model; other leaves (numbers, booleans) are copied verbatim,
        // since "translating" them would just corrupt them.
        const pending = translatableKeys.filter((key) =>
            staleKeys.has(key) || targetFlat[key] === undefined || targetFlat[key] === "")

        // Requested together rather than one after another; the gate around the API call caps how
        // many are actually in flight. Results are collected by key so completion order does not
        // matter, and the output ordering is rebuilt from en.json just below.
        const translated: FlatDict = {}
        await Promise.all(pending.map(async (key) => {
            const value = await translateText(client, enFlat[key], targetLanguage)
            if (value === undefined) {
                failedKeys.add(key)
                console.log(`[${languageCode}] '${key}': translation failed, leaving the existing value in place.`)
                return
            }
            translated[key] = value
            console.log(`[${languageCode}] '${key}': ${JSON.stringify(enFlat[key])} -> ${JSON.stringify(value)}`)
        }))

        // Assembled in en.json key order so the output mirrors the reference. This keeps
        // regeneration from reordering existing key/value pairs — which would otherwise open PRs
        // that only rearrange keys — and, by iterating enFlat, also drops any key no longer present
        // in en.json. A key that was neither translated nor already present falls back to the
        // English text, so every language file keeps key parity with the reference even when a
        // translation was skipped.
        const result: FlatDict = {}
        for (const key of Object.keys(enFlat)) {
            result[key] = translated[key] ?? targetFlat[key] ?? enFlat[key]
        }

        const removed = Object.keys(targetFlat).filter((key) => !(key in enFlat))
        if (removed.length) {
            console.log(`[${languageCode}] Removed ${removed.length} key(s) not in en.json: ${removed.join(", ")}`)
        }

        writeIfChanged(targetPath, JSON.stringify({[languageCode]: unflattenDict(result)}, null, 2))
    }))

    // Recorded only after every language has been written, and only for keys that made it through
    // all of them: the fingerprint asserts "every language carries a translation of this exact
    // English text", so a key that failed anywhere must not be marked as settled. Keys dropped from
    // en.json fall out here, since the file is rebuilt from `translatableKeys`.
    const nextFingerprints: Fingerprints = {}
    for (const key of translatableKeys) {
        if (failedKeys.has(key)) {
            if (fingerprints[key]) nextFingerprints[key] = fingerprints[key]
            continue
        }
        nextFingerprints[key] = fingerprintOf(enFlat[key])
    }
    writeFingerprints(fingerprintsFile, nextFingerprints)
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

export interface TranslateLocaleFilesOptions {
    client: TranslationClient;
    /** Absolute paths of the `*.locale.ts` files to fill. */
    localeFiles: string[];
    /**
     * Absolute path to the fingerprints file for these locale files. Entries are keyed
     * `<path relative to the fingerprints file>|<flat key>`, so one file covers them all.
     */
    fingerprintsFile?: string;
    /** Re-translate every key, ignoring the fingerprints. */
    force?: boolean;
}

/**
 * Translates the missing/stale keys of each `*.locale.ts` file in place, using that file's own
 * `en` block as the source of truth for every other language.
 */
export async function translateLocaleFiles(options: TranslateLocaleFilesOptions): Promise<void> {
    const {client, localeFiles, fingerprintsFile, force = false} = options

    const fingerprints = readFingerprints(fingerprintsFile)
    const nextFingerprints: Fingerprints = {}
    const fingerprintKeyFor = (filePath: string, key: string): string =>
        `${fingerprintsFile ? relative(dirname(fingerprintsFile), filePath) : filePath}|${key}`

    await Promise.all(localeFiles.map(async (filePath) => {
        const data = evalLocaleModule(readFileSync(filePath, "utf-8"))
        if (!data.en) {
            console.log(`Skipping ${filePath}: no 'en' base translations found.`)
            return
        }

        const enFlat = flattenDict(data.en)
        const translatableKeys = Object.keys(enFlat).filter((key) => typeof enFlat[key] === "string")
        const staleKeys = new Set(
            force
                ? translatableKeys
                : translatableKeys.filter((key) =>
                    fingerprints[fingerprintKeyFor(filePath, key)] !== fingerprintOf(enFlat[key])),
        )

        const failedKeys = new Set<string>()

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

            const pending = translatableKeys.filter((key) =>
                staleKeys.has(key) || targetFlat[key] === undefined || targetFlat[key] === "")

            const translated: FlatDict = {}
            await Promise.all(pending.map(async (key) => {
                const value = await translateText(client, enFlat[key], targetLanguage)
                if (value === undefined) {
                    failedKeys.add(key)
                    console.log(`[${filePath}] '${key}': translation failed, leaving the existing value in place.`)
                    return
                }
                translated[key] = value
                console.log(`[${filePath}] '${key}': ${JSON.stringify(enFlat[key])} -> ${JSON.stringify(value)}`)
            }))

            // Rebuild the target dict in the same key order as `en`, dropping keys no longer present in `en`.
            const result: FlatDict = {}
            for (const key of Object.keys(enFlat)) {
                result[key] = translated[key] ?? targetFlat[key] ?? enFlat[key]
            }
            return [code, unflattenDict(result)] as const
        }))

        for (const key of translatableKeys) {
            const fingerprintKey = fingerprintKeyFor(filePath, key)
            if (failedKeys.has(key)) {
                if (fingerprints[fingerprintKey]) nextFingerprints[fingerprintKey] = fingerprints[fingerprintKey]
                continue
            }
            nextFingerprints[fingerprintKey] = fingerprintOf(enFlat[key])
        }

        // Assembled after the fact in a fixed order — `en` first, then `codes` — rather than as each
        // language finishes. The serialised output preserves key order, so letting completion order
        // decide it would rewrite the whole file on every run.
        const result: {[lang: string]: NestedDict} = {en: data.en}
        for (const [code, dict] of translatedByCode) {
            result[code] = dict
        }

        writeIfChanged(filePath, serializeLocaleModule(result))
    }))

    writeFingerprints(fingerprintsFile, nextFingerprints)
}
