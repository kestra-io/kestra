/**
 * Validates that every *.locale.ts file under the design-system components
 * directory is complete: all supported languages are present and each
 * non-English language has exactly the same set of nested keys as English.
 *
 * This test is intentionally kept simple and fast — it runs in jsdom with no
 * Vue runtime.  Its sole purpose is to catch missing or extra translation keys
 * introduced by contributors before they reach the autotranslate CI action.
 */
import {describe, it, expect} from "vitest"

// ─── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Recursively collect every key path in a nested object, e.g.
 * { a: { b: 1, c: 2 } } → ["a", "a.b", "a.c"]
 */
const getNestedKeys = (obj: Record<string, unknown>, prefix = ""): string[] =>
    Object.keys(obj).reduce<string[]>((keys, key) => {
        const fullKey = prefix ? `${prefix}.${key}` : key
        keys.push(fullKey)
        if (typeof obj[key] === "object" && obj[key] !== null) {
            keys.push(...getNestedKeys(obj[key] as Record<string, unknown>, fullKey))
        }
        return keys
    }, [])

// ─── Constants ───────────────────────────────────────────────────────────────

const SUPPORTED_LANGUAGES = [
    "de",
    "en",
    "es",
    "fr",
    "hi",
    "it",
    "ja",
    "ko",
    "pl",
    "pt",
    "pt_BR",
    "ru",
    "zh_CN",
] as const

type Language = (typeof SUPPORTED_LANGUAGES)[number]
const NON_ENGLISH_LANGUAGES = SUPPORTED_LANGUAGES.filter((l) => l !== "en") as Language[]

// ─── Load all locale modules eagerly ─────────────────────────────────────────

const localeModules = import.meta.glob<{default: Record<Language, Record<string, unknown>>}>(
    "../../src/components/**/*.locale.ts",
    {eager: true},
)

// ─── Tests ───────────────────────────────────────────────────────────────────

describe("design-system locale files", () => {
    it("at least one locale file is found", () => {
        expect(Object.keys(localeModules).length).toBeGreaterThan(0)
    })

    for (const [filePath, module] of Object.entries(localeModules)) {
        // Keep the path short for readability in test output
        const shortPath = filePath.replace(/^.*\/components\//, "")
        const translations = module.default

        describe(shortPath, () => {
            it("has all supported languages", () => {
                const missingLanguages = SUPPORTED_LANGUAGES.filter(
                    (lang) => !(lang in translations),
                )
                expect(
                    missingLanguages,
                    `Missing language entries: ${missingLanguages.join(", ")}`,
                ).toEqual([])
            })

            it("every language has the same keys as English", () => {
                const englishKeys = getNestedKeys((translations.en ?? {}) as Record<string, unknown>)
                const errors: string[] = []

                for (const lang of NON_ENGLISH_LANGUAGES) {
                    if (!(lang in translations)) {
                        // Already reported by the "has all supported languages" test above
                        continue
                    }

                    const langKeys = getNestedKeys(
                        translations[lang] as Record<string, unknown>,
                    )
                    const missing = englishKeys.filter((k) => !langKeys.includes(k))
                    const extra = langKeys.filter((k) => !englishKeys.includes(k))

                    if (missing.length) {
                        errors.push(`[${lang}] missing keys: ${missing.join(", ")}`)
                    }
                    if (extra.length) {
                        errors.push(`[${lang}] extra keys: ${extra.join(", ")}`)
                    }
                }

                expect(errors, errors.join("\n")).toEqual([])
            })
        })
    }
})
