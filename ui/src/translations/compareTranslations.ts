import fs from "fs"
import path from "path"
import {baseCompile} from "@intlify/message-compiler"

const DEFAULT_LANGUAGES = ["de", "es", "fr", "hi", "it", "ja", "ko", "pl", "pt", "pt_BR", "ru", "zh_CN"]

const readJSON = (filePath: string): Record<string, unknown> => JSON.parse(fs.readFileSync(filePath, "utf-8"))

const getNestedStrings = (obj: Record<string, unknown>, prefix = ""): Record<string, string> =>
    Object.keys(obj).reduce((strings: Record<string, string>, key: string) => {
        const fullKey = prefix ? `${prefix}.${key}` : key
        const value = obj[key]
        if (typeof value === "string") strings[fullKey] = value
        else if (typeof value === "object" && value && !Array.isArray(value)) {
            Object.assign(strings, getNestedStrings(value as Record<string, unknown>, fullKey))
        }
        return strings
    }, {})

/**
 * Runs a message through vue-i18n's own compiler and returns its errors. This is the ground truth for
 * "will `t()` throw at render time" — most importantly it rejects `{{name}}`, which is a compile error
 * (`Not allowed nest placeholder`) and not, as it looks, a harmless variant of `{name}`.
 */
const compileErrors = (message: string): string[] => {
    const errors: string[] = []
    baseCompile(message, {onError: (e) => errors.push(e.message)})
    return errors
}

/**
 * Placeholder names a message interpolates, e.g. `Flow {namespace}.{id}` -> `["id", "namespace"]`.
 * Deduplicated, so a locale that collapses `{count} rule | {count} rules` into a single plural form
 * still matches English. `{'literal'}` escapes are not placeholders and are skipped.
 */
const placeholders = (message: string): string[] => {
    // `\{` / `\}` backslash escapes and `{'...'}` literal blocks are text, not interpolation
    const scannable = message.replace(/\\[{}]/g, "").replace(/\{'[^']*'\}/g, "")
    return [...new Set([...scannable.matchAll(/\{\s*([^{}\s][^{}]*?)\s*\}/g)].map((m) => m[1]))].sort()
}

const getNestedKeys = (obj: Record<string, unknown>, prefix = ""): string[] =>
    Object.keys(obj).reduce((keys: string[], key: string) => {
        const fullKey = prefix ? `${prefix}.${key}` : key
        keys.push(fullKey)
        if (typeof obj[key] === "object" && obj[key] && !Array.isArray(obj[key])) {
            keys.push(...getNestedKeys(obj[key] as Record<string, unknown>, fullKey))
        }
        return keys
    }, [])

/**
 * Compares every language file in `translationsDir` against the English base and throws if any key
 * is missing or extra, if any message fails to compile, or if a translation's interpolation
 * placeholders differ from English. Shared by OSS (`kestra/ui`) and EE (`kestra-ee/ui-ee`) — each
 * caller passes the directory that holds its own language JSON files, which is what repoints the
 * check at EE.
 *
 * @param translationsDir absolute path to the folder containing `en.json` and the locale files
 * @param languages       language codes to check (defaults to all shipped locales)
 */
export function compareTranslations(translationsDir: string, languages: string[] = DEFAULT_LANGUAGES): void {
    const getPath = (lang: string): string => path.resolve(translationsDir, `${lang}.json`)

    // Use English as a base language
    const englishRoot = readJSON(getPath("en"))["en"] as Record<string, unknown>
    const content = getNestedKeys(englishRoot)
    const englishStrings = getNestedStrings(englishRoot)

    const globalMissing: Record<string, string[]> = {}
    const globalExtra: Record<string, string[]> = {}
    const globalUncompilable: Record<string, string[]> = {}
    const globalPlaceholderDrift: Record<string, string[]> = {}

    const checkCompiles = (lang: string, strings: Record<string, string>): string[] => {
        const broken = Object.entries(strings).flatMap(([key, message]) => {
            const errors = compileErrors(message)
            return errors.length ? [`${key}: ${errors.join("; ")} — ${JSON.stringify(message)}`] : []
        })
        if (broken.length) globalUncompilable[lang] = broken
        return broken
    }

    // English is the source every locale is generated from, so a broken message there spreads.
    const englishBroken = checkCompiles("en", englishStrings)
    console.warn(`---\n\x1b[34mComparison with EN\x1b[0m  \n`)
    console.warn(englishBroken.length ? `Uncompilable messages: \x1b[31m${englishBroken.join("\n  ")}\x1b[0m` : "No uncompilable messages.")
    console.warn("---\n")

    languages.forEach((lang) => {
        const root = readJSON(getPath(lang))[lang] as Record<string, unknown>
        const current = getNestedKeys(root)
        const strings = getNestedStrings(root)

        const missing = content.filter((key) => !current.includes(key))
        const extra = current.filter((key) => !content.includes(key))

        const drift = Object.entries(strings).flatMap(([key, message]) => {
            const english = englishStrings[key]
            if (english === undefined) return []
            const expected = placeholders(english)
            const actual = placeholders(message)
            if (expected.length === actual.length && expected.every((name, i) => name === actual[i])) return []
            return [`${key}: expected {${expected.join("}, {")}} but found {${actual.join("}, {")}} — ${JSON.stringify(message)}`]
        })

        console.warn(`---\n\x1b[34mComparison with ${lang.toUpperCase()}\x1b[0m  \n`)
        console.warn(missing.length ? `Missing keys: \x1b[31m${missing.join(", ")}\x1b[0m` : "No missing keys.")
        console.warn(extra.length ? `Extra keys: \x1b[32m${extra.join(", ")}\x1b[0m` : "No extra keys.")

        const broken = checkCompiles(lang, strings)
        console.warn(broken.length ? `Uncompilable messages: \x1b[31m${broken.join("\n  ")}\x1b[0m` : "No uncompilable messages.")
        console.warn(drift.length ? `Placeholder mismatches: \x1b[31m${drift.join("\n  ")}\x1b[0m` : "No placeholder mismatches.")
        console.warn("---\n")

        if (missing.length) globalMissing[lang] = missing
        if (extra.length) globalExtra[lang] = extra
        if (drift.length) globalPlaceholderDrift[lang] = drift
    })

    let errorString = ""
    if (Object.keys(globalMissing).length) {
        errorString += "\nMissing keys in translations"
    }

    if (Object.keys(globalExtra).length) {
        errorString += "\nExtra keys in translations"
    }

    if (Object.keys(globalUncompilable).length) {
        errorString += "\nUncompilable messages in translations (vue-i18n throws on these at render time — placeholders use a single pair of braces, `{name}` not `{{name}}`)"
    }

    if (Object.keys(globalPlaceholderDrift).length) {
        errorString += "\nPlaceholder mismatches in translations (each translation must interpolate exactly the placeholders its English source does)"
    }

    if (errorString.length) {
        throw new Error(errorString)
    }
}
