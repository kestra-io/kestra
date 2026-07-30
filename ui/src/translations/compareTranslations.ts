import fs from "fs"
import path from "path"

const DEFAULT_LANGUAGES = ["de", "es", "fr", "hi", "it", "ja", "ko", "pl", "pt", "pt_BR", "ru", "zh_CN"]

const readJSON = (filePath: string): Record<string, unknown> => JSON.parse(fs.readFileSync(filePath, "utf-8"))

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
 * is missing or extra. Shared by OSS (`kestra/ui`) and EE (`kestra-ee/ui-ee`) — each caller passes
 * the directory that holds its own language JSON files, which is what repoints the check at EE.
 *
 * @param translationsDir absolute path to the folder containing `en.json` and the locale files
 * @param languages       language codes to check (defaults to all shipped locales)
 */
export function compareTranslations(translationsDir: string, languages: string[] = DEFAULT_LANGUAGES): void {
    const getPath = (lang: string): string => path.resolve(translationsDir, `${lang}.json`)

    // Use English as a base language
    const content = getNestedKeys(readJSON(getPath("en"))["en"] as Record<string, unknown>)

    const globalMissing: Record<string, string[]> = {}
    const globalExtra: Record<string, string[]> = {}

    languages.forEach((lang) => {
        const current = getNestedKeys(readJSON(getPath(lang))[lang] as Record<string, unknown>)

        const missing = content.filter((key) => !current.includes(key))
        const extra = current.filter((key) => !content.includes(key))

        console.warn(`---\n\x1b[34mComparison with ${lang.toUpperCase()}\x1b[0m  \n`)
        console.warn(missing.length ? `Missing keys: \x1b[31m${missing.join(", ")}\x1b[0m` : "No missing keys.")
        console.warn(extra.length ? `Extra keys: \x1b[32m${extra.join(", ")}\x1b[0m` : "No extra keys.")
        console.warn("---\n")

        if (missing.length) globalMissing[lang] = missing
        if (extra.length) globalExtra[lang] = extra
    })

    let errorString = ""
    if (Object.keys(globalMissing).length) {
        errorString += "\nMissing keys in translations"
    }

    if (Object.keys(globalExtra).length) {
        errorString += "\nExtra keys in translations"
    }

    if (errorString.length) {
        throw new Error(errorString)
    }
}
