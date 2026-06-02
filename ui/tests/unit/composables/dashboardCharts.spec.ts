import {describe, it, expect} from "vitest"
import {groupStackedBars} from "../../../src/components/dashboard/composables/charts"

describe("groupStackedBars", () => {
    const rows = [
        {namespace: "company.demo", state: "SUCCESS", total: 12},
        {namespace: "company.demo", state: "WARNING", total: 2},
        {namespace: "company.demo", state: "FAILED", total: 6},
        {namespace: "company.data", state: "SUCCESS", total: 9},
        {namespace: "company.data", state: "FAILED", total: 4},
        {namespace: "qa.batch5", state: "SUCCESS", total: 8},
        {namespace: "qa.batch5", state: "PAUSED", total: 1},
    ]

    it("creates one series per distinct sub-key, not one per (bar × sub-key)", () => {
        const {labels, datasets} = groupStackedBars(rows, "namespace", ["state"], "total")

        expect(labels).toEqual(["company.demo", "company.data", "qa.batch5"])
        expect(datasets.map((d) => d.label)).toEqual(["SUCCESS", "WARNING", "FAILED", "PAUSED"])
    })

    it("spreads each series across all bars so the tooltip stays bounded by sub-key cardinality", () => {
        const {datasets} = groupStackedBars(rows, "namespace", ["state"], "total")

        const success = datasets.find((d) => d.label === "SUCCESS")
        expect(success?.data).toEqual([12, 9, 8])

        const paused = datasets.find((d) => d.label === "PAUSED")
        expect(paused?.data).toEqual([0, 0, 1])
    })

    it("preserves per-sub-key totals when collapsing the explosion", () => {
        const {datasets} = groupStackedBars(rows, "namespace", ["state"], "total")
        const sum = (label: string) =>
            datasets.find((d) => d.label === label)!.data.reduce((a, b) => a + b, 0)

        expect(sum("SUCCESS")).toBe(29)
        expect(sum("FAILED")).toBe(10)
    })

    it("collapses to a single series when there is no sub-dimension", () => {
        const simple = [
            {namespace: "a", total: 3},
            {namespace: "b", total: 5},
        ]
        const {labels, datasets} = groupStackedBars(simple, "namespace", [], "total")

        expect(labels).toEqual(["a", "b"])
        expect(datasets).toHaveLength(1)
        expect(datasets[0].data).toEqual([3, 5])
    })

    it("returns empty structures for missing data", () => {
        expect(groupStackedBars(undefined, "namespace", ["state"], "total")).toEqual({labels: [], datasets: []})
    })
})
