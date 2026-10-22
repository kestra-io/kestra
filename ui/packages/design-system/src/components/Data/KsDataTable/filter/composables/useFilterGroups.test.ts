
import {describe, test, expect} from "vitest"
import {
    Comparators,
    type AppliedFilter,
    type FilterGroup,
    type LeafFilterGroup,
    type WrapperGroup,
} from "../utils/filterTypes"
import {
    newGroupId,
    emptyLeafGroup,
    allFilters,
    findLeafContaining,
    findLeafById,
} from "./useFilterGroups"

const makeFilter = (overrides: Partial<AppliedFilter> = {}): AppliedFilter => ({
    id: "f1",
    key: "key",
    keyLabel: "Key",
    comparator: Comparators.CONTAINS,
    comparatorLabel: "Contains",
    value: "foo",
    valueLabel: "foo",
    ...overrides,
})

const makeLeaf = (id: string, filters: AppliedFilter[] = []): LeafFilterGroup => ({
    id,
    kind: "leaf",
    filters,
})

const makeWrapper = (id: string, children: LeafFilterGroup[]): WrapperGroup => ({
    id,
    kind: "wrapper",
    logical: "AND",
    children,
})

describe("useFilterGroups helpers", () => {
    describe("newGroupId", () => {
        test("returns a non-empty string", () => {
            const id = newGroupId()
            expect(typeof id).toBe("string")
            expect(id.length).toBeGreaterThan(0)
        })

        test("generates different IDs on consecutive calls", () => {
            expect(newGroupId()).not.toBe(newGroupId())
        })
    })

    describe("emptyLeafGroup", () => {
        test("creates a leaf group with no filters and a non-empty ID", () => {
            const group = emptyLeafGroup()

            expect(group.kind).toBe("leaf")
            expect(group.filters).toEqual([])
            expect(group.id).toEqual(expect.any(String))
            expect(group.id.length).toBeGreaterThan(0)
        })

        test("generates a different ID for each group", () => {
            expect(emptyLeafGroup().id).not.toBe(emptyLeafGroup().id)
        })
    })

    describe("allFilters", () => {
        test("collects filters from top-level leaf groups", () => {
            const first = makeFilter({id: "f1"})
            const second = makeFilter({id: "f2", key: "other"})

            const groups: FilterGroup[] = [
                makeLeaf("leaf-1", [first]),
                makeLeaf("leaf-2", [second]),
            ]

            expect(allFilters(groups)).toEqual([first, second])
        })

        test("collects filters from children of wrapper groups", () => {
            const first = makeFilter({id: "f1"})
            const second = makeFilter({id: "f2", key: "other"})
            const groups: FilterGroup[] = [
                makeWrapper("wrapper-1", [
                    makeLeaf("child-1", [first]),
                    makeLeaf("child-2", [second]),
                ]),
            ]

            expect(allFilters(groups)).toEqual([first, second])
        })

        test("returns an empty array when there are no groups", () => {
            expect(allFilters([])).toEqual([])
        })
    })

    describe("findLeafContaining", () => {
        test("finds a top-level leaf containing the requested filter", () => {
            const filter = makeFilter({id: "f1"})
            const leaf = makeLeaf("leaf-1", [filter])

            expect(findLeafContaining([leaf], "f1")).toBe(leaf)
        })

        test("finds a child leaf inside a wrapper", () => {
            const filter = makeFilter({id: "f1"})
            const child = makeLeaf("child-1", [filter])
            const wrapper = makeWrapper("wrapper-1", [child])

            expect(findLeafContaining([wrapper], "f1")).toBe(child)
        })

        test("returns undefined when the filter is not found", () => {
            expect(findLeafContaining([makeLeaf("leaf-1")], "missing")).toBeUndefined()
        })
    })

    describe("findLeafById", () => {
        test("finds a top-level leaf by its ID", () => {
            const leaf = makeLeaf("leaf-1")

            expect(findLeafById([leaf], "leaf-1")).toBe(leaf)
        })

        test("finds a child leaf inside a wrapper by its ID", () => {
            const child = makeLeaf("child-1")
            const wrapper = makeWrapper("wrapper-1", [child])

            expect(findLeafById([wrapper], "child-1")).toBe(child)
        })

        test("returns undefined when the leaf ID is not found", () => {
            expect(findLeafById([makeLeaf("leaf-1")], "missing")).toBeUndefined()
        })
    })
})
