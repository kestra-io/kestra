import fs from "fs"
import path from "path"
import {fileURLToPath} from "url"

type TranslationMap = Record<string, unknown>

const getPath = (lang: string): string => path.resolve(path.dirname(fileURLToPath(import.meta.url)), `./${lang}.json`)
const readJSON = (filePath: string): TranslationMap => JSON.parse(fs.readFileSync(filePath, "utf-8"))

const getNestedKeys = (obj: TranslationMap, prefix: string = ""): string[] =>
    Object.keys(obj).reduce<string[]>((keys, key) => {
        const fullKey = prefix ? `${prefix}.${key}` : key
        keys.push(fullKey)
        if (typeof obj[key] === "object" && obj[key]) {
            keys.push(...getNestedKeys(obj[key] as TranslationMap, fullKey))
        }
        return keys
    }, [])

// Use English as a base language
const content = getNestedKeys(readJSON(getPath("en"))["en"] as TranslationMap)

const languages = ["de", "es", "fr", "hi", "it", "ja", "ko", "pl", "pt", "pt_BR", "ru", "zh_CN"]
const paths = languages.map((lang) => getPath(lang))

const globalMissing: Record<string, string[]> = {}
const globalExtra: Record<string, string[]> = {}

languages.forEach((lang, i) => {
    const current = getNestedKeys(readJSON(paths[i])[lang] as TranslationMap)

    const missing = content.filter((key) => !current.includes(key))
    const extra = current.filter((key) => !content.includes(key))

    console.log(`---\n\x1b[34mComparison with ${lang.toUpperCase()}\x1b[0m  \n`)
    console.log(missing.length ? `Missing keys: \x1b[31m${missing.join(", ")}\x1b[0m` : "No missing keys.")
    console.log(extra.length ? `Extra keys: \x1b[32m${extra.join(", ")}\x1b[0m` : "No extra keys.")
    console.log("---\n")

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
