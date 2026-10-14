import fs from "fs"
import path from "path"
import {baseCompile} from "@intlify/message-compiler"
import {readFingerprints, staleKeys} from "./fingerprints.ts"
import {flattenStrings, leafKeys, placeholdersOf, untranslatedKeys} from "./translationRules.mjs"

import {TRANSLATED_LOCALES} from "../../src/translations/languages.ts"

// Re-exported under the name the entry points already use.
export {TRANSLATED_LOCALES as DEFAULT_LANGUAGES}

const readJSON = (filePath: string): Record<string, unknown> => JSON.parse(fs.readFileSync(filePath, "utf-8"))

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
 * Compares every language file in `translationsDir` against the English base and throws if any key
 * is missing or extra, if any message fails to compile, or if a translation's interpolation
 * placeholders differ from English. Shared by OSS (`kestra/ui`) and EE (`kestra-ee/ui-ee`) — each
 * caller passes the directory that holds its own language JSON files, which is what repoints the
 * check at EE.
 *
 * Also reports *stale* keys: ones whose English source has changed since the translations were
 * generated from it. Key parity alone never caught those — the translation is present, just of
 * text that no longer exists — which is how, for example, an EE rename of "SuperAdmin" to
 * "Superadmin" sat un-propagated in eleven locales for a year (kestra-io/kestra#10656).
 *
 * @param translationsDir  absolute path to the folder containing `en.json` and the locale files
 * @param languages        language codes to check (defaults to all shipped locales)
 * @param fingerprintsFile absolute path to the generator's fingerprints file; omit to skip the
 *                         staleness check
 */
export function compareTranslations(
    translationsDir: string,
    languages: readonly string[] = TRANSLATED_LOCALES,
    fingerprintsFile?: string,
): void {
    // Locale codes are interpolated into a path, so they are constrained to the shape a locale
    // actually has, and the resolved path is then asserted to sit directly inside the base
    // directory. Callers pass a fixed list today; the two guards mean a future caller reading codes
    // from a directory listing or an argument cannot escape `translationsDir` whatever it passes.
    const baseDir = path.resolve(translationsDir)
    const getPath = (lang: string): string => {
        if (!/^[a-z]{2}(_[A-Z]{2})?$/.test(lang)) {
            throw new Error(`"${lang}" is not a valid locale code.`)
        }
        const resolved = path.resolve(baseDir, `${lang}.json`)
        if (path.dirname(resolved) !== baseDir) {
            throw new Error(`Refusing to read "${resolved}": it is outside "${baseDir}".`)
        }
        return resolved
    }

    // Use English as a base language
    const englishRoot = readJSON(getPath("en"))["en"] as Record<string, unknown>
    // Leaf keys, not every node: `generateTranslations.ts` rebuilds each language file from the
    // English string leaves, so an object node carrying no message - `pluginDefaults: {}` - is never
    // written to a locale file and must not be reported missing from one. Counting nodes made this
    // check disagree with the PR gate in `./check-translations.mjs`, which has always used leaves,
    // and left `translations:check` failing on a clean develop for three keys nobody could fix.
    const content = leafKeys(englishRoot)
    const englishStrings = flattenStrings(englishRoot)

    // A key is stale when the English text no longer hashes to what it did when the translations
    // were last generated. Same hash the generator writes — see `generateTranslations.ts`.
    const stale = fingerprintsFile && fs.existsSync(fingerprintsFile)
        ? staleKeys(englishRoot, readFingerprints(fingerprintsFile))
        : []

    const globalMissing: Record<string, string[]> = {}
    const globalExtra: Record<string, string[]> = {}
    const globalUncompilable: Record<string, string[]> = {}
    const globalPlaceholderDrift: Record<string, string[]> = {}
    const globalUntranslated: Record<string, string[]> = {}

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
    console.warn("---\n\x1b[34mComparison with EN\x1b[0m  \n")
    console.warn(englishBroken.length ? `Uncompilable messages: \x1b[31m${englishBroken.join("\n  ")}\x1b[0m` : "No uncompilable messages.")
    console.warn(stale.length ? `Stale keys (English changed since translating): \x1b[31m${stale.join(", ")}\x1b[0m` : "No stale keys.")
    console.warn("---\n")

    languages.forEach((lang) => {
        const root = readJSON(getPath(lang))[lang] as Record<string, unknown>
        const current = leafKeys(root)
        const strings = flattenStrings(root)

        const missing = content.filter((key) => !current.includes(key))
        const extra = current.filter((key) => !content.includes(key))

        const drift = Object.entries(strings).flatMap(([key, message]) => {
            const english = englishStrings[key]
            if (english === undefined) return []
            const expected = placeholdersOf(english)
            const actual = placeholdersOf(message)
            if (expected.length === actual.length && expected.every((name, i) => name === actual[i])) return []
            return [`${key}: expected {${expected.join("}, {")}} but found {${actual.join("}, {")}} — ${JSON.stringify(message)}`]
        })

        const untranslated = untranslatedKeys(lang, strings, englishStrings)

        console.warn(`---\n\x1b[34mComparison with ${lang.toUpperCase()}\x1b[0m  \n`)
        console.warn(missing.length ? `Missing keys: \x1b[31m${missing.join(", ")}\x1b[0m` : "No missing keys.")
        console.warn(extra.length ? `Extra keys: \x1b[32m${extra.join(", ")}\x1b[0m` : "No extra keys.")

        const broken = checkCompiles(lang, strings)
        console.warn(broken.length ? `Uncompilable messages: \x1b[31m${broken.join("\n  ")}\x1b[0m` : "No uncompilable messages.")
        console.warn(drift.length ? `Placeholder mismatches: \x1b[31m${drift.join("\n  ")}\x1b[0m` : "No placeholder mismatches.")
        console.warn(untranslated.length ? `Untranslated keys (still the English text): \x1b[31m${untranslated.join(", ")}\x1b[0m` : "No untranslated keys.")
        console.warn("---\n")

        if (missing.length) globalMissing[lang] = missing
        if (extra.length) globalExtra[lang] = extra
        if (drift.length) globalPlaceholderDrift[lang] = drift
        if (untranslated.length) globalUntranslated[lang] = untranslated
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

    if (Object.keys(globalUntranslated).length) {
        errorString += "\nUntranslated messages: a locale whose script is not Latin still holds the untouched English text, which is what `translations:generate` writes when a translation request fails. Blank those values and re-run it."
    }

    if (stale.length) {
        errorString += `\nStale translations: the English source of ${stale.length} key(s) changed after they were translated. Run \`npm run translations:generate\` to bring the other languages back in line.`
    }

    if (errorString.length) {
        throw new Error(errorString)
    }
}
