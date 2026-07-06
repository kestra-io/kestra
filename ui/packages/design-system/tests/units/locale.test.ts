import {describe, test, expect} from "vitest"

const localeModules = import.meta.glob<{default: Record<string, unknown>}>(
    "../../src/components/**/*.json",
    {eager: true},
)

const LANGUAGES = ["en", "de", "es", "fr", "hi", "it", "ja", "ko", "pl", "pt", "pt_BR", "ru", "zh_CN"]

const FILE_PATTERN = /^(.*)\.([a-z]{2}(?:_[A-Z]{2})?)\.json$/

function getNestedKeys(obj: Record<string, unknown>, prefix = ""): string[] {
    return Object.keys(obj).reduce<string[]>((keys, key) => {
        const fullKey = prefix ? `${prefix}.${key}` : key
        keys.push(fullKey)
        const value = obj[key]
        if (value && typeof value === "object" && !Array.isArray(value)) {
            keys.push(...getNestedKeys(value as Record<string, unknown>, fullKey))
        }
        return keys
    }, [])
}

// Group per-language files back by their component (e.g. "KsEmpty") so each
// component's translations can be checked as a set, mirroring src/translations/check.js.
const byComponent = new Map<string, Map<string, Record<string, unknown>>>()
for (const [path, mod] of Object.entries(localeModules)) {
    const match = path.match(FILE_PATTERN)
    if (!match) continue
    const [, base, lang] = match
    if (!byComponent.has(base)) {
        byComponent.set(base, new Map())
    }
    byComponent.get(base)!.set(lang, mod.default)
}

describe("design-system locale files", () => {
    test("found at least one component locale file", () => {
        expect(byComponent.size).toBeGreaterThan(0)
    })

    for (const [base, langMap] of byComponent) {
        const componentName = base.split("/").pop()

        test(`${componentName} has a file for every supported language`, () => {
            expect([...langMap.keys()].sort()).toEqual([...LANGUAGES].sort())
        })

        test(`${componentName} translations match the English key set`, () => {
            const enKeys = getNestedKeys(langMap.get("en") ?? {}).sort()
            for (const lang of LANGUAGES) {
                if (lang === "en") continue
                const keys = getNestedKeys(langMap.get(lang) ?? {}).sort()
                expect(keys, `${componentName} (${lang})`).toEqual(enKeys)
            }
        })
    }
})
