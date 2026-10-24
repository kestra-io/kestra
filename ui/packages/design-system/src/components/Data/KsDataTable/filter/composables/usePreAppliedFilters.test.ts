import {describe, expect, test} from "vitest"
import {Comparators, type AppliedFilter} from "../utils/filterTypes"
import {usePreAppliedFilters} from "./usePreAppliedFilters"

const makeFilter = (overrides: Partial<AppliedFilter> = {}): AppliedFilter => ({
    id: "f1",
    key: "status",
    keyLabel: "Status",
    comparator: Comparators.CONTAINS,
    comparatorLabel: "Contains",
    value: "SUCCESS",
    valueLabel: "SUCCESS",
    ...overrides,
})

describe("usePreAppliedFilters", () => {
    test("records every filter passed to markAsPreApplied", () => {
        const preApplied = usePreAppliedFilters()
        const filters = [
            makeFilter({key: "status"}),
            makeFilter({id: "f2", key: "namespace"}),
        ]

        preApplied.markAsPreApplied(filters)

        expect(preApplied.isPreApplied("status")).toBe(true)
        expect(preApplied.isPreApplied("namespace")).toBe(true)
        expect(preApplied.getAllPreApplied()).toEqual(filters)
    })

    test("stores a copy instead of the caller's filter object", () => {
        const preApplied = usePreAppliedFilters()
        const filter = makeFilter()

        preApplied.markAsPreApplied([filter])

        expect(preApplied.getPreApplied("status")).toEqual(filter)
        expect(preApplied.getPreApplied("status")).not.toBe(filter)
    })

    test("returns true for marked keys and false for unknown keys", () => {
        const preApplied = usePreAppliedFilters()
        preApplied.markAsPreApplied([makeFilter({key: "status"})])

        expect(preApplied.isPreApplied("status")).toBe(true)
        expect(preApplied.isPreApplied("unknown")).toBe(false)
        expect(preApplied.hasPreApplied("status")).toBe(true)
        expect(preApplied.hasPreApplied("unknown")).toBe(false)
    })

    test("returns undefined for an unknown key", () => {
        const preApplied = usePreAppliedFilters()

        expect(preApplied.getPreApplied("unknown")).toBeUndefined()
    })

    test("returns every stored filter", () => {
        const preApplied = usePreAppliedFilters()
        const filters = [
            makeFilter({key: "status"}),
            makeFilter({id: "f2", key: "namespace"}),
            makeFilter({id: "f3", key: "flow"}),
        ]

        preApplied.markAsPreApplied(filters)

        expect(preApplied.getAllPreApplied()).toEqual(filters)
    })

    test("overwrites a filter when the same key is marked twice", () => {
        const preApplied = usePreAppliedFilters()
        const original = makeFilter({value: "FAILED"})
        const replacement = makeFilter({value: "SUCCESS"})

        preApplied.markAsPreApplied([original])
        preApplied.markAsPreApplied([replacement])

        expect(preApplied.getPreApplied("status")).toEqual(replacement)
        expect(preApplied.getAllPreApplied()).toEqual([replacement])
        expect(preApplied.getAllPreApplied()).toHaveLength(1)
    })

    test("clears only the specified key", () => {
        const preApplied = usePreAppliedFilters()
        const filters = [
            makeFilter({key: "status"}),
            makeFilter({id: "f2", key: "namespace"}),
        ]

        preApplied.markAsPreApplied(filters)
        preApplied.clearPreAppliedKey("status")

        expect(preApplied.isPreApplied("status")).toBe(false)
        expect(preApplied.hasPreApplied("status")).toBe(false)
        expect(preApplied.getPreApplied("status")).toBeUndefined()

        expect(preApplied.isPreApplied("namespace")).toBe(true)
        expect(preApplied.hasPreApplied("namespace")).toBe(true)
        expect(preApplied.getAllPreApplied()).toEqual([filters[1]])
    })

    test("clears all pre-applied keys and stored defaults", () => {
        const preApplied = usePreAppliedFilters()
        const filters = [
            makeFilter({key: "status"}),
            makeFilter({id: "f2", key: "namespace"}),
        ]

        preApplied.markAsPreApplied(filters)
        preApplied.clearPreApplied()

        expect(preApplied.isPreApplied("status")).toBe(false)
        expect(preApplied.isPreApplied("namespace")).toBe(false)
        expect(preApplied.hasPreApplied("status")).toBe(false)
        expect(preApplied.hasPreApplied("namespace")).toBe(false)
        expect(preApplied.getPreApplied("status")).toBeUndefined()
        expect(preApplied.getPreApplied("namespace")).toBeUndefined()
        expect(preApplied.getAllPreApplied()).toEqual([])
    })
})