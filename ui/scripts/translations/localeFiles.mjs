// Reading the design-system `*.locale.ts` files.
//
// Unlike the per-language JSON files, each of these bundles every language in a single default
// export:
//
//   export default { en: {...}, de: {...}, ... }
//
// They are parsed rather than evaluated, since a locale file may come from a fork.

import fs from "node:fs"
import path from "node:path"
import {fingerprintOf, flattenStrings} from "./fingerprintRules.mjs"
import {untranslatedKeys} from "./translationRules.mjs"

const ESCAPES = {n: "\n", t: "\t", r: "\r", b: "\b", f: "\f", v: "\v"}

/**
 * Parse a `*.locale.ts` default export into a plain object. The files are data literals, and one of
 * them may come from a pull request from a fork, so this accepts only nested objects and string
 * literals and throws on anything else instead of evaluating it.
 */
export function parseLocaleModule(source) {
    let pos = 0

    const fail = (what) => {
        throw new Error(`Cannot parse the locale file: ${what} at offset ${pos}.`)
    }

    const skipBlank = () => {
        for (;;) {
            if (/\s/.test(source[pos] ?? "")) {
                pos++
            } else if (source.startsWith("//", pos)) {
                const end = source.indexOf("\n", pos)
                pos = end === -1 ? source.length : end
            } else if (source.startsWith("/*", pos)) {
                const end = source.indexOf("*/", pos + 2)
                if (end === -1) fail("unterminated comment")
                pos = end + 2
            } else {
                return
            }
        }
    }

    const parseString = () => {
        const quote = source[pos++]
        let out = ""
        for (;;) {
            const c = source[pos++]
            if (c === undefined) fail("unterminated string")
            if (c === quote) return out
            if (quote === "`" && c === "$" && source[pos] === "{") fail("template interpolation")
            if (c === "\r" && source[pos] === "\n") continue
            if ((c === "\n" || c === "\r") && quote !== "`") fail("line break in a string")
            if (c !== "\\") {
                out += c
                continue
            }
            const e = source[pos++]
            if (e === "\r" && source[pos] === "\n") pos++
            if (e === "u" && /^[0-9a-fA-F]{4}/.test(source.slice(pos, pos + 4))) {
                out += String.fromCharCode(parseInt(source.slice(pos, pos + 4), 16))
                pos += 4
            } else if (e === "x" && /^[0-9a-fA-F]{2}/.test(source.slice(pos, pos + 2))) {
                out += String.fromCharCode(parseInt(source.slice(pos, pos + 2), 16))
                pos += 2
            } else if (e === "0" && !/\d/.test(source[pos] ?? "")) {
                out += "\0"
            } else if (e in ESCAPES && e !== "0") {
                out += ESCAPES[e]
            } else if (e === "\n" || e === "\r") {
                continue
            } else if (e !== undefined && /^[^A-Za-z0-9]$/.test(e)) {
                out += e
            } else {
                fail("unsupported escape")
            }
        }
    }

    const parseKey = () => {
        if (/["'`]/.test(source[pos] ?? "")) return parseString()
        const match = /^[A-Za-z_$][\w$]*/.exec(source.slice(pos))
        if (!match) fail("expected a key")
        pos += match[0].length
        return match[0]
    }

    const parseValue = () => {
        skipBlank()
        if (/["'`]/.test(source[pos] ?? "")) return parseString()
        if (source[pos] !== "{") fail("expected an object or a string")
        pos++
        const object = Object.create(null)
        for (;;) {
            skipBlank()
            if (source[pos] === "}") {
                pos++
                return {...object}
            }
            const key = parseKey()
            skipBlank()
            if (source[pos++] !== ":") fail("expected ':'")
            object[key] = parseValue()
            skipBlank()
            if (source[pos] === ",") pos++
            else if (source[pos] !== "}") fail("expected ',' or '}'")
        }
    }

    skipBlank()
    const exported = /^export\s+default\b/.exec(source.slice(pos))
    if (!exported) fail("expected 'export default'")
    pos += exported[0].length
    skipBlank()
    if (source[pos] !== "{") fail("expected an object")
    const value = parseValue()
    skipBlank()
    if (source[pos] === ";") pos++
    skipBlank()
    if (pos !== source.length) fail("unexpected content after the export")
    return value
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
        const data = parseLocaleModule(fs.readFileSync(localeFile, "utf-8"))
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
        const data = parseLocaleModule(fs.readFileSync(localeFile, "utf-8"))
        if (!data.en) return []
        return Object.entries(flattenStrings(data.en))
            .filter(([key, message]) =>
                fingerprints[localeFingerprintKey(fingerprintsFile, localeFile, key)] !== fingerprintOf(message))
            .map(([key]) => ({file: path.basename(localeFile), key}))
    })
}
