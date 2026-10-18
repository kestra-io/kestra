import {describe, expect, it} from "vitest"
import type {languages} from "monaco-editor/editor/editor.api"
import {withIndentedBlockScalarContent} from "./yamlBlockScalarTokenizer"

type MonarchRule = languages.IMonarchLanguageRule
type MonarchRuleObject = {cases: Record<string, unknown>}

describe("yamlBlockScalarTokenizer", () => {
    describe("withIndentedBlockScalarContent", () => {
        it("returns a language without a multiStringContinued tokenizer state unchanged", () => {
            const withoutContinued: languages.IMonarchLanguage = {
                tokenizer: {
                    root: [[/^[a-z]+$/, "keyword"]],
                },
            }

            const result = withIndentedBlockScalarContent(withoutContinued)
            expect(result).toBe(withoutContinued)

            const emptyLanguage: languages.IMonarchLanguage = {
                tokenizer: {},
            }
            expect(withIndentedBlockScalarContent(emptyLanguage)).toBe(emptyLanguage)
        })

        it("replaces multiStringContinued when present in the language", () => {
            const originalContinued: MonarchRule[] = [
                [/^.*$/, "string"],
            ]
            const input: languages.IMonarchLanguage = {
                tokenizer: {
                    root: [[/^[a-z]+$/, "keyword"]],
                    multiStringContinued: originalContinued,
                },
            }

            const result = withIndentedBlockScalarContent(input)
            expect(result).not.toBe(input)
            expect(result.tokenizer.multiStringContinued).not.toBe(originalContinued)
            expect(result.tokenizer.multiStringContinued).toHaveLength(1)
        })

        it("does not mutate the original language object", () => {
            const originalContinued: MonarchRule[] = [
                [/^.*$/, "string"],
            ]
            const input: languages.IMonarchLanguage = {
                tokenizer: {
                    root: [[/^[a-z]+$/, "keyword"]],
                    multiStringContinued: originalContinued,
                },
            }

            withIndentedBlockScalarContent(input)
            expect(input.tokenizer.multiStringContinued).toBe(originalContinued)
        })

        it("carries over every other tokenizer state untouched", () => {
            const rootState: MonarchRule[] = [[/^[a-z]+$/, "keyword"]]
            const commentState: MonarchRule[] = [[/^#.*$/, "comment"]]
            const originalContinued: MonarchRule[] = [[/^.*$/, "string"]]

            const input: languages.IMonarchLanguage = {
                tokenizer: {
                    root: rootState,
                    comment: commentState,
                    multiStringContinued: originalContinued,
                },
            }

            const result = withIndentedBlockScalarContent(input)
            expect(result.tokenizer.root).toBe(rootState)
            expect(result.tokenizer.comment).toBe(commentState)
        })

        it("configures the replacement rule to keep a more-indented line inside the block rather than ending it", () => {
            const input: languages.IMonarchLanguage = {
                tokenizer: {
                    multiStringContinued: [[/^.*$/, "string"]],
                },
            }

            const result = withIndentedBlockScalarContent(input)
            const rule = result.tokenizer.multiStringContinued[0] as [RegExp, MonarchRuleObject]

            const [pattern, action] = rule
            expect(pattern).toEqual(/^( *).+$/)
            // The case "$1~$S2 *" matches the captured indentation ($1) against the block
            // indentation ($S2) plus optional additional spaces, keeping it highlighted as "string"
            expect(action.cases["$1~$S2 *"]).toBe("string")
        })

        it("configures the replacement rule so that a line indented less than the block start ends the block", () => {
            const input: languages.IMonarchLanguage = {
                tokenizer: {
                    multiStringContinued: [[/^.*$/, "string"]],
                },
            }

            const result = withIndentedBlockScalarContent(input)
            const rule = result.tokenizer.multiStringContinued[0] as [RegExp, MonarchRuleObject]

            const [, action] = rule
            // When indentation does not match "$S2 *" (e.g. less indented than block start),
            // it triggers "@default", which rematches the line and pops back to parent tokenizer state
            expect(action.cases["@default"]).toEqual({
                token: "@rematch",
                next: "@popall",
            })
        })
    })
})
