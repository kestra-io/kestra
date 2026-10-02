import {describe, expect, it} from "vitest"
import {
    buildDiffHunks,
    computeSelectionSummary,
    distinctSkipReasons,
    getSeparatorVariant,
    inlineReplacement,
    type SourceSearchDiffMatch,
    type SourceSearchSelectionGroup,
} from "../../../src/utils/sourceSearchDiff"
import {crossSearchResultKey} from "../../../src/utils/crossResourceSearch"

describe("distinctSkipReasons", () => {
    it("deduplicates reasons and orders them by severity", () => {
        expect(distinctSkipReasons([
            {reason: "NO_CHANGE"},
            {reason: "READ_ONLY"},
            {reason: "NO_CHANGE"},
        ])).toEqual(["READ_ONLY", "NO_CHANGE"])
    })

    it("falls back to UNKNOWN for reasons it does not recognize", () => {
        expect(distinctSkipReasons([{reason: "SOMETHING_NEW"}])).toEqual(["UNKNOWN"])
        expect(distinctSkipReasons([{}])).toEqual(["UNKNOWN"])
    })

    it("drops an unrecognized reason when a known one is also present", () => {
        expect(distinctSkipReasons([{reason: "READ_ONLY"}, {reason: "SOMETHING_NEW"}])).toEqual(["READ_ONLY"])
    })
})

const group = (namespace: string, id: string, editable: boolean, lines: number[]): SourceSearchSelectionGroup => ({
    namespace,
    id,
    editable,
    matches: lines.map((line) => ({line, column: 0})),
})

const keysOf = (groups: SourceSearchSelectionGroup[]) => new Set(groups.flatMap((g) =>
    g.matches.map((match) => crossSearchResultKey({type: "flows", namespace: g.namespace, id: g.id, line: match.line, column: match.column}))))

describe("computeSelectionSummary", () => {
    it("counts matches selected with the keys FlowsSearch actually stores", () => {
        // Given selections keyed exactly as the page keys them — crossSearchResultKey, "flows:"-prefixed
        const groups = [group("company.data", "daily-etl", true, [4, 12])]

        // When
        const summary = computeSelectionSummary(groups, keysOf(groups))

        // Then the confirm bar reports the real counts, not 0/0
        expect(summary).toEqual({selectedFlowCount: 1, selectedMatchCount: 2})
    })

    it("does not match a key in the pre-cross-resource unprefixed format", () => {
        const groups = [group("company.data", "daily-etl", true, [4])]

        expect(computeSelectionSummary(groups, new Set(["company.data.daily-etl#4:0"])))
            .toEqual({selectedFlowCount: 0, selectedMatchCount: 0})
    })

    it("skips read-only groups even when their matches are selected", () => {
        const editable = group("company.data", "daily-etl", true, [4])
        const readOnly = group("prod.payments", "reconcile-ledger", false, [9])

        expect(computeSelectionSummary([editable, readOnly], keysOf([editable, readOnly])))
            .toEqual({selectedFlowCount: 1, selectedMatchCount: 1})
    })

    it("counts a group once when only some of its matches are selected", () => {
        const groups = [group("company.data", "daily-etl", true, [4, 12, 20])]
        const partial = new Set([crossSearchResultKey({type: "flows", namespace: "company.data", id: "daily-etl", line: 12, column: 0})])

        expect(computeSelectionSummary(groups, partial)).toEqual({selectedFlowCount: 1, selectedMatchCount: 1})
    })

    it("returns zero counts for an empty selection", () => {
        expect(computeSelectionSummary([group("ns", "id", true, [1])], new Set()))
            .toEqual({selectedFlowCount: 0, selectedMatchCount: 0})
    })
})

describe("getSeparatorVariant", () => {
    it("should replace hyphens with underscores", () => {
        expect(getSeparatorVariant("my-flow")).toBe("my_flow")
    })

    it("should replace underscores with hyphens", () => {
        expect(getSeparatorVariant("my_flow")).toBe("my-flow")
    })

    it("should return null when the query has no separator", () => {
        expect(getSeparatorVariant("myflow")).toBeNull()
    })

    it("should return null for mixed separators", () => {
        expect(getSeparatorVariant("my-flow_name")).toBeNull()
    })

    it("should return null for an empty query", () => {
        expect(getSeparatorVariant("")).toBeNull()
    })
})

describe("buildDiffHunks", () => {
    const sourceLines = [
        "line 1",
        "line 2",
        "line 3",
        "line 4",
        "line 5",
        "line 6",
        "line 7",
        "line 8",
        "line 9",
        "line 10",
    ]

    it("returns no hunks when there are no matches", () => {
        expect(buildDiffHunks(sourceLines, [])).toEqual([])
    })

    it("produces a hunk with context lines for a single changed region", () => {
        const matches: SourceSearchDiffMatch[] = [
            {line: 5, before: "line 5", after: "changed line 5"},
        ]

        const result = buildDiffHunks(sourceLines, matches, 2)

        // Context before: lines 3, 4
        // Changed: line 5 (removed + added)
        // Context after: lines 6, 7
        expect(result).toEqual([
            {kind: "context", line: 3, text: "line 3"},
            {kind: "context", line: 4, text: "line 4"},
            {kind: "removed", line: 5, text: "line 5"},
            {kind: "added", line: 5, text: "changed line 5"},
            {kind: "context", line: 6, text: "line 6"},
            {kind: "context", line: 7, text: "line 7"},
        ])
    })

    it("produces separate hunks for non-adjacent changed regions", () => {
        const matches: SourceSearchDiffMatch[] = [
            {line: 3, before: "line 3", after: "changed line 3"},
            {line: 8, before: "line 8", after: "changed line 8"},
        ]

        const result = buildDiffHunks(sourceLines, matches, 1)

        // First hunk: lines 2-4
        // Second hunk: lines 7-9
        expect(result).toEqual([
            {kind: "context", line: 2, text: "line 2"},
            {kind: "removed", line: 3, text: "line 3"},
            {kind: "added", line: 3, text: "changed line 3"},
            {kind: "context", line: 4, text: "line 4"},
            {kind: "context", line: 7, text: "line 7"},
            {kind: "removed", line: 8, text: "line 8"},
            {kind: "added", line: 8, text: "changed line 8"},
            {kind: "context", line: 9, text: "line 9"},
        ])
    })

    it("merges adjacent changes into a single hunk when ranges overlap", () => {
        const matches: SourceSearchDiffMatch[] = [
            {line: 4, before: "line 4", after: "changed line 4"},
            {line: 5, before: "line 5", after: "changed line 5"},
        ]

        const result = buildDiffHunks(sourceLines, matches, 1)

        // With context=1: first match spans lines 3-5, second spans 4-6
        // These overlap (4-5), so they merge into lines 3-6
        expect(result).toEqual([
            {kind: "context", line: 3, text: "line 3"},
            {kind: "removed", line: 4, text: "line 4"},
            {kind: "added", line: 4, text: "changed line 4"},
            {kind: "removed", line: 5, text: "line 5"},
            {kind: "added", line: 5, text: "changed line 5"},
            {kind: "context", line: 6, text: "line 6"},
        ])
    })

    it("merges nearby changes when context ranges touch", () => {
        const matches: SourceSearchDiffMatch[] = [
            {line: 3, before: "line 3", after: "changed line 3"},
            {line: 5, before: "line 5", after: "changed line 5"},
        ]

        const result = buildDiffHunks(sourceLines, matches, 1)

        // First match: lines 2-4, second: lines 4-6
        // They touch at line 4, so merge into lines 2-6
        expect(result).toEqual([
            {kind: "context", line: 2, text: "line 2"},
            {kind: "removed", line: 3, text: "line 3"},
            {kind: "added", line: 3, text: "changed line 3"},
            {kind: "context", line: 4, text: "line 4"},
            {kind: "removed", line: 5, text: "line 5"},
            {kind: "added", line: 5, text: "changed line 5"},
            {kind: "context", line: 6, text: "line 6"},
        ])
    })

    it("does not extend before the first line when match is on line 1", () => {
        const matches: SourceSearchDiffMatch[] = [
            {line: 1, before: "line 1", after: "changed line 1"},
        ]

        const result = buildDiffHunks(sourceLines, matches, 2)

        // Should start at line 1, not line -1
        expect(result).toEqual([
            {kind: "removed", line: 1, text: "line 1"},
            {kind: "added", line: 1, text: "changed line 1"},
            {kind: "context", line: 2, text: "line 2"},
            {kind: "context", line: 3, text: "line 3"},
        ])
        // Verify no line < 1
        expect(result.every((l) => l.line >= 1)).toBe(true)
    })

    it("does not extend beyond the last line when match is on the last line", () => {
        const matches: SourceSearchDiffMatch[] = [
            {line: 10, before: "line 10", after: "changed line 10"},
        ]

        const result = buildDiffHunks(sourceLines, matches, 2)

        // Should end at line 10, not line 12
        expect(result).toEqual([
            {kind: "context", line: 8, text: "line 8"},
            {kind: "context", line: 9, text: "line 9"},
            {kind: "removed", line: 10, text: "line 10"},
            {kind: "added", line: 10, text: "changed line 10"},
        ])
        // Verify no line > sourceLines.length
        expect(result.every((l) => l.line <= sourceLines.length)).toBe(true)
    })

    it("uses the first match when multiple matches exist on the same line", () => {
        const matches: SourceSearchDiffMatch[] = [
            {line: 5, before: "line 5", after: "changed line 5 v1"},
            {line: 5, before: "line 5", after: "changed line 5 v2"},
        ]

        const result = buildDiffHunks(sourceLines, matches, 1)

        // Implementation uses find() which returns only the first match per line
        const line5Removed = result.filter((l) => l.line === 5 && l.kind === "removed")
        const line5Added = result.filter((l) => l.line === 5 && l.kind === "added")
        expect(line5Removed).toHaveLength(1)
        expect(line5Added).toHaveLength(1)
        expect(line5Removed[0].text).toBe("line 5")
        expect(line5Added[0].text).toBe("changed line 5 v1")
    })

    it("uses default context of 2 when not specified", () => {
        const matches: SourceSearchDiffMatch[] = [
            {line: 5, before: "line 5", after: "changed line 5"},
        ]

        const result = buildDiffHunks(sourceLines, matches)

        // Default context is 2, so lines 3-7
        expect(result[0]).toEqual({kind: "context", line: 3, text: "line 3"})
        expect(result[result.length - 1]).toEqual({kind: "context", line: 7, text: "line 7"})
    })
})

describe("inlineReplacement", () => {
    const baseContext = {
        query: "test",
        regex: true,
        caseSensitive: false,
        wholeWord: false,
    } as const

    it("replaces the matched portion when regex is enabled", () => {
        const context = {...baseContext, replacement: "replaced"}
        expect(inlineReplacement("this is a test string", context)).toBe("this is a replaced string")
    })

    it("replaces only the first occurrence when regex is enabled", () => {
        const context = {...baseContext, replacement: "X"}
        expect(inlineReplacement("test test test", context)).toBe("X test test")
    })

    it("respects case sensitivity", () => {
        const context = {...baseContext, caseSensitive: true, replacement: "X"}
        expect(inlineReplacement("Test test", context)).toBe("Test X")
    })

    it("respects whole word matching", () => {
        const context = {...baseContext, wholeWord: true, replacement: "X"}
        expect(inlineReplacement("test testing attest", context)).toBe("X testing attest")
    })

    it("returns replacement directly when regex is disabled", () => {
        const context = {...baseContext, regex: false, replacement: "literal replacement"}
        expect(inlineReplacement("any matched text", context)).toBe("literal replacement")
    })

    it("handles replacement text with dollar sign literally", () => {
        const context = {...baseContext, replacement: "$100"}
        expect(inlineReplacement("price is test", context)).toBe("price is $100")
    })

    it("handles replacement text with backslash literally", () => {
        const context = {...baseContext, replacement: "C:\\path"}
        expect(inlineReplacement("path is test", context)).toBe("path is C:\\path")
    })

    it("handles replacement text with regex special characters literally", () => {
        const context = {...baseContext, replacement: "*.[ ]"}
        expect(inlineReplacement("pattern is test", context)).toBe("pattern is *.[ ]")
    })

    it("handles replacement text with multiple special characters", () => {
        const context = {...baseContext, replacement: "$1\\$2.*[]"}
        expect(inlineReplacement("special test chars", context)).toBe("special $1\\$2.*[] chars")
    })

    it("falls back to replacement when regex pattern is invalid", () => {
        const context = {...baseContext, query: "[invalid", replacement: "fallback"}
        expect(inlineReplacement("anything", context)).toBe("fallback")
    })

    it("handles empty matched string", () => {
        const context = {...baseContext, replacement: "X"}
        expect(inlineReplacement("", context)).toBe("")
    })

    it("handles empty replacement", () => {
        const context = {...baseContext, replacement: ""}
        expect(inlineReplacement("test string", context)).toBe(" string")
    })
})
