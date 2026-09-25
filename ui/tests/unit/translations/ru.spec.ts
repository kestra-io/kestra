import {describe, expect, it} from "vitest"
import translations from "../../../src/translations/ru.json"

interface RuTranslations {
    ru: {block_editor: {namespace_kv: string; namespace_secrets: string; namespace_files: string}}
}

const ru: RuTranslations = translations

const LOOKALIKE_LATIN = /[ABCEHKMOPTXacepoxy]/
const CYRILLIC = /[Ѐ-ӿ]/

function wordsWithHomoglyph(value: string): string[] {
    return value.split(/[^\p{L}]+/u).filter(word => word && CYRILLIC.test(word) && LOOKALIKE_LATIN.test(word))
}

describe("ru.json block_editor labels", () => {
    // Regression: "namespace_secrets" shipped as "Cекреты" — a Latin "C" (U+0043) standing in for the
    // Cyrillic "С" (U+0421). It renders identically, so only a codepoint-level check catches it. A
    // reserved English term sharing the string (e.g. namespace_files: "Файлы namespace") is its own,
    // all-Latin word and must not trip this — the check flags a word mixing both scripts, not whitespace.
    it("has no Latin letter homoglyph mixed into a Cyrillic word", () => {
        const {namespace_kv, namespace_secrets, namespace_files} = ru.ru.block_editor
        for (const value of [namespace_kv, namespace_secrets, namespace_files]) {
            expect(wordsWithHomoglyph(value)).toEqual([])
        }
    })
})
