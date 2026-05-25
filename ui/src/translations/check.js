import fs from "fs"
import path from "path"
import {fileURLToPath} from "url"

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const getPath = (lang) => path.resolve(__dirname, `./${lang}.json`)
const readJSON = (filePath) => JSON.parse(fs.readFileSync(filePath, "utf-8"))

const getNestedKeys = (obj, prefix = "") =>
    Object.keys(obj).reduce((keys, key) => {
        const fullKey = prefix ? `${prefix}.${key}` : key
        keys.push(fullKey)
        if (
            typeof obj[key] === "object" &&
            obj[key]
        ) {
            keys.push(...getNestedKeys(obj[key], fullKey))
        }
        return keys
    }, [])

// ─── JSON translations check ─────────────────────────────────────────────────

// Use English as a base language
const content = getNestedKeys(readJSON(getPath("en"))["en"])

const languages = ["de", "es", "fr", "hi", "it", "ja", "ko", "pl", "pt", "pt_BR", "ru", "zh_CN"]
const paths = languages.map((lang) => getPath(lang))

const globalMissing = {}
const globalExtra = {}

languages.forEach((lang, i) => {
    const current = getNestedKeys(readJSON(paths[i])[lang])

    const missing = content.filter((key) => !current.includes(key))
    const extra = current.filter((key) => !content.includes(key))

    console.log(`---\n\x1b[34mComparison with ${lang.toUpperCase()}\x1b[0m  \n`)
    console.log(missing.length ? `Missing keys: \x1b[31m${missing.join(", ")}\x1b[0m` : "No missing keys.")
    console.log(extra.length ? `Extra keys: \x1b[32m${extra.join(", ")}\x1b[0m` : "No extra keys.")
    console.log("---\n")

    if(missing.length) globalMissing[lang] = missing
    if(extra.length) globalExtra[lang] = extra
})

// ─── Design-system *.locale.ts files check ───────────────────────────────────

/**
 * Recursively find all files whose names end with ".locale.ts" under `dir`.
 * @param {string} dir
 * @returns {string[]}
 */
function findLocaleFiles(dir) {
    const results = []
    for (const item of fs.readdirSync(dir, {withFileTypes: true})) {
        const fullPath = path.join(dir, item.name)
        if (item.isDirectory()) {
            results.push(...findLocaleFiles(fullPath))
        } else if (item.name.endsWith(".locale.ts")) {
            results.push(fullPath)
        }
    }
    return results
}

/**
 * Evaluate a *.locale.ts file that uses the pattern `export default { ... }`.
 * The files contain only a plain object literal — no TypeScript type
 * annotations — so stripping the ESM export keyword and using the Function
 * constructor is safe and avoids any build-step dependency.
 *
 * @param {string} filePath
 * @returns {Record<string, Record<string, unknown>>}
 */
function readLocaleTs(filePath) {
    const raw = fs.readFileSync(filePath, "utf-8")
    const objectLiteral = raw
        .replace(/^export\s+default\s+/, "")
        .trimEnd()
        .replace(/;$/, "")
    // The Function constructor is intentional here: locale files are
    // build-time assets under our own control and contain only data.
     
    return new Function("return " + objectLiteral)()
}

const designSystemComponentsDir = path.resolve(
    __dirname,
    "../../packages/design-system/src/components",
)

const localeFiles = findLocaleFiles(designSystemComponentsDir)

const globalMissingLocale = {}
const globalExtraLocale = {}

for (const localeFile of localeFiles) {
    const relativePath = path.relative(designSystemComponentsDir, localeFile)
    const translations = readLocaleTs(localeFile)
    const englishKeys = getNestedKeys(translations.en ?? {})

    console.log(`\n=== Checking design-system locale: ${relativePath} ===\n`)

    // Check that all supported (non-English) language sections are present
    const missingLangs = languages.filter((lang) => !(lang in translations))
    if (missingLangs.length) {
        console.log(`Missing language sections: \x1b[31m${missingLangs.join(", ")}\x1b[0m`)
    }

    for (const lang of languages) {
        if (!(lang in translations)) continue

        const langKeys = getNestedKeys(translations[lang])
        const missing = englishKeys.filter((k) => !langKeys.includes(k))
        const extra = langKeys.filter((k) => !englishKeys.includes(k))

        console.log(`---\n\x1b[34mComparison with ${lang.toUpperCase()} in ${relativePath}\x1b[0m  \n`)
        console.log(missing.length ? `Missing keys: \x1b[31m${missing.join(", ")}\x1b[0m` : "No missing keys.")
        console.log(extra.length ? `Extra keys: \x1b[32m${extra.join(", ")}\x1b[0m` : "No extra keys.")
        console.log("---\n")

        if (missing.length) {
            if (!globalMissingLocale[lang]) globalMissingLocale[lang] = {}
            globalMissingLocale[lang][relativePath] = missing
        }
        if (extra.length) {
            if (!globalExtraLocale[lang]) globalExtraLocale[lang] = {}
            globalExtraLocale[lang][relativePath] = extra
        }
    }
}

// ─── Final error check ───────────────────────────────────────────────────────

let errorString = ""

if (Object.keys(globalMissing).length) {
    errorString += "\nMissing keys in JSON translations"
}
if (Object.keys(globalExtra).length) {
    errorString += "\nExtra keys in JSON translations"
}
if (Object.keys(globalMissingLocale).length) {
    errorString += "\nMissing keys in design-system locale.ts files"
}
if (Object.keys(globalExtraLocale).length) {
    errorString += "\nExtra keys in design-system locale.ts files"
}

if (errorString.length) {
    throw new Error(errorString)
}
