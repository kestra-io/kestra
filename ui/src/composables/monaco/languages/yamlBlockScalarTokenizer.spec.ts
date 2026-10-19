import {describe, expect, it} from "vitest"
import type {languages} from "monaco-editor/editor/editor.api"

import {withIndentedBlockScalarContent} from "./yamlBlockScalarTokenizer"

describe("withIndentedBlockScalarContent", () => {
    it("returns a language without multiStringContinued unchanged", () => {
        const language = {
            tokenizer: {
                root: [],
            },
        } as unknown as languages.IMonarchLanguage

        expect(withIndentedBlockScalarContent(language)).toBe(language)
    })

    it("replaces the multiStringContinued tokenizer state", () => {
        const originalRules = [[/original/, "original"]] as unknown as languages.IMonarchLanguageRule[]

        const language = {
            tokenizer: {
                root: [],
                multiStringContinued: originalRules,
            },
        } as unknown as languages.IMonarchLanguage

        const result = withIndentedBlockScalarContent(language)

        expect(result).not.toBe(language)
        expect(result.tokenizer?.multiStringContinued).not.toBe(originalRules)
        expect(result.tokenizer?.multiStringContinued).toHaveLength(1)
    })

    it("does not mutate the original language object", () => {
        const originalRules = [[/original/, "original"]] as unknown as languages.IMonarchLanguageRule[]

        const language = {
            tokenizer: {
                root: [],
                multiStringContinued: originalRules,
            },
        } as unknown as languages.IMonarchLanguage

        const originalTokenizer = language.tokenizer

        const result = withIndentedBlockScalarContent(language)

        expect(language.tokenizer).toBe(originalTokenizer)
        expect(language.tokenizer?.multiStringContinued).toBe(originalRules)
        expect(result.tokenizer).not.toBe(originalTokenizer)
    })

    it("preserves every other tokenizer state", () => {
        const rootRules = [[/root/, "root"]] as unknown as languages.IMonarchLanguageRule[]
        const commentRules = [[/comment/, "comment"]] as unknown as languages.IMonarchLanguageRule[]
        const originalRules = [[/original/, "original"]] as unknown as languages.IMonarchLanguageRule[]

        const language = {
            tokenizer: {
                root: rootRules,
                comment: commentRules,
                multiStringContinued: originalRules,
            },
        } as unknown as languages.IMonarchLanguage

        const result = withIndentedBlockScalarContent(language)

        expect(result.tokenizer?.root).toBe(rootRules)
        expect(result.tokenizer?.comment).toBe(commentRules)
    })

    it("keeps more-indented block content inside the string state", () => {
        const language = {
            tokenizer: {
                root: [],
                multiStringContinued: [],
            },
        } as unknown as languages.IMonarchLanguage

        const result = withIndentedBlockScalarContent(language)
        const rules = result.tokenizer?.multiStringContinued
        const [pattern, action] = rules![0] as unknown as [
            RegExp,
            {cases: Record<string, unknown>},
        ]

        expect(pattern).toEqual(/^( *).+$/)
        expect(action.cases["$1~$S2 *"]).toBe("string")
    })

    it("ends the block when a line is less indented than its content", () => {
        const language = {
            tokenizer: {
                root: [],
                multiStringContinued: [],
            },
        } as unknown as languages.IMonarchLanguage

        const result = withIndentedBlockScalarContent(language)
        const rules = result.tokenizer?.multiStringContinued
        const [, action] = rules![0] as unknown as [
            RegExp,
            {cases: Record<string, unknown>},
        ]

        expect(action.cases["@default"]).toEqual({
            token: "@rematch",
            next: "@popall",
        })
    })
})
