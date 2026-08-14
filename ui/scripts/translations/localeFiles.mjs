// Reading the design-system `*.locale.ts` files.
//
// Unlike the per-language JSON files, each of these bundles every language in a single default
// export:
//
//   export default { en: {...}, de: {...}, ... }
//
// They hold only string values and nested objects — no imports, types or function calls — which
// lets us evaluate them as plain object literals rather than parsing TypeScript. Dependency-free,
// so the PR gate can check them too.

import fs from "node:fs"
import path from "node:path"
import {fingerprintOf, flattenStrings} from "./fingerprintRules.mjs"
import {untranslatedKeys} from "./translationRules.mjs"

/** Evaluate the body of a `*.locale.ts` default export into a plain object. */
export function evalLocaleModule(source) {
    const body = source
        .replace(/export\s+default\s*/, "")
        .replace(/;?\s*$/, "")
    return new Function(`return (${body})`)()
}

/**
 * Fingerprint key for one entry, matching what the generator writes: the file's path relative to
 * the fingerprints file, then the flat key.
 */
export function localeFingerprintKey(fingerprintsFile, localeFile, key) {
    return `${path.relative(path.dirname(fingerprintsFile), localeFile)}|${key}`
}

/**
 * `{file, key}` for every design-system string whose English source changed after it was last
 * translated. Same drift the language JSON files are checked for — these were simply never looked
 * at, so a reworded `KsEmpty` or `KsDurationPicker` string could sit un-propagated indefinitely.
 */
/**
 * `{file, lang, key}` for every design-system string still holding its English text verbatim.
 *
 * The same English-fallback fault the language JSON files have - see `untranslatedKeys` in
 * ./translationRules.mjs - reaches these files through the same generator, and the fingerprints
 * cannot expose it: they record the English source, which is exactly what got written.
 */
export function untranslatedLocaleEntries(localeFiles) {
    return localeFiles.flatMap((localeFile) => {
        const data = evalLocaleModule(fs.readFileSync(localeFile, "utf-8"))
        if (!data.en) return []
        const english = flattenStrings(data.en)

        return Object.keys(data)
            .filter((lang) => lang !== "en")
            .flatMap((lang) => untranslatedKeys(lang, flattenStrings(data[lang]), english)
                .map((key) => ({file: path.basename(localeFile), lang, key})))
    })
}

export function staleLocaleEntries(localeFiles, fingerprintsFile) {
    if (!fs.existsSync(fingerprintsFile)) return []
    const fingerprints = JSON.parse(fs.readFileSync(fingerprintsFile, "utf-8"))

    return localeFiles.flatMap((localeFile) => {
        const data = evalLocaleModule(fs.readFileSync(localeFile, "utf-8"))
        if (!data.en) return []
        return Object.entries(flattenStrings(data.en))
            .filter(([key, message]) =>
                fingerprints[localeFingerprintKey(fingerprintsFile, localeFile, key)] !== fingerprintOf(message))
            .map(([key]) => ({file: path.basename(localeFile), key}))
    })
}
