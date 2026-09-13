import {describe, expect, it} from "vitest"
import {computeSelectionSummary, distinctSkipReasons, type SourceSearchSelectionGroup} from "../../../src/utils/sourceSearchDiff"
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
