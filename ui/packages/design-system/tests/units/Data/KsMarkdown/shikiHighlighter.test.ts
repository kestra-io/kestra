import {describe, test, expect, vi} from "vitest"
import {loadLanguageOnDemand} from "../../../../src/components/Data/KsMarkdown/shikiHighlighter"

let attempts = 0

vi.mock("shiki/langs", () => {
    attempts++
    if (1 === attempts) {
        throw new Error("Failed to fetch dynamically imported module")
    }
    return {bundledLanguages: {elixir: () => Promise.resolve({})}}
})

const highlighter = {
    loadLanguage: vi.fn().mockResolvedValue(undefined),
} as never

describe("loadLanguageOnDemand", () => {
    test("reports failure instead of rejecting when the grammar bundle cannot be fetched, and retries afterwards", async () => {
        await expect(loadLanguageOnDemand(highlighter, "elixir")).resolves.toBe(false)

        await expect(loadLanguageOnDemand(highlighter, "elixir")).resolves.toBe(true)
    })

    test("uses a no-grammar language without fetching the bundle", async () => {
        const before = attempts

        await expect(loadLanguageOnDemand(highlighter, "ansi")).resolves.toBe(true)

        expect(attempts).toBe(before)
    })
})
