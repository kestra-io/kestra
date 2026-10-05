import {describe, expect, it} from "vitest"
import {
    buildDiffHunks,
    computeSelectionSummary,
    distinctSkipReasons,
    getSeparatorVariant,
    inlineReplacement,
    type ReplaceContext,
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
    const sourceLines = Array.from({length: 10}, (_, i) => `line ${i + 1}`)
    const change = (line: number): SourceSearchDiffMatch => ({line, before: `line ${line}`, after: `changed ${line}`})
    const context = (line: number) => ({kind: "context", line, text: `line ${line}`})
    const changed = (line: number) => [
        {kind: "removed", line, text: `line ${line}`},
        {kind: "added", line, text: `changed ${line}`},
    ]

    it("surrounds a change with two context lines by default", () => {
        expect(buildDiffHunks(sourceLines, [change(5)])).toEqual([
            context(3), context(4), ...changed(5), context(6), context(7),
        ])
    })

    it("keeps distant changes in separate hunks, in line order", () => {
        expect(buildDiffHunks(sourceLines, [change(8), change(3)], 1)).toEqual([
            context(2), ...changed(3), context(4),
            context(7), ...changed(8), context(9),
        ])
    })

    it("merges overlapping changes instead of repeating the shared context line", () => {
        expect(buildDiffHunks(sourceLines, [change(3), change(5)], 1)).toEqual([
            context(2), ...changed(3), context(4), ...changed(5), context(6),
        ])
    })

    it("does not run off either end of the file", () => {
        expect(buildDiffHunks(sourceLines, [change(1)])).toEqual([...changed(1), context(2), context(3)])
        expect(buildDiffHunks(sourceLines, [change(10)])).toEqual([context(8), context(9), ...changed(10)])
    })
})

describe("inlineReplacement", () => {
    const replace = (matched: string, overrides: Partial<ReplaceContext>) => inlineReplacement(matched, {
        query: "test",
        replacement: "X",
        regex: true,
        caseSensitive: false,
        wholeWord: false,
        ...overrides,
    })

    it("substitutes the match and leaves the rest of the text intact", () => {
        expect(replace("a test string", {})).toBe("a X string")
    })

    it("inserts regex-special characters in the replacement literally", () => {
        expect(replace("a test", {replacement: "*.[ ]"})).toBe("a *.[ ]")
    })

    it("honours case sensitivity", () => {
        expect(replace("Test test", {caseSensitive: true})).toBe("Test X")
    })

    it("applies whole-word matching to every alternative of the query", () => {
        expect(replace("cats dog", {query: "cat|dog", wholeWord: true})).toBe("cats X")
    })

    it("returns the replacement as is when regex is off or the pattern is invalid", () => {
        expect(replace("a test", {regex: false})).toBe("X")
        expect(replace("a test", {query: "[invalid"})).toBe("X")
    })
})
