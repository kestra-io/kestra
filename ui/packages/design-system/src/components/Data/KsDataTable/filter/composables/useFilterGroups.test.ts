import {describe, expect, test} from "vitest"
import {Comparators, type AppliedFilter, type FilterGroup, type LeafFilterGroup, type WrapperGroup} from "../utils/filterTypes"
import {useFilterGroups} from "./useFilterGroups"

const makeFilter = (id: string): AppliedFilter => ({
    id,
    key: "status",
    keyLabel: "Status",
    comparator: Comparators.EQUALS,
    comparatorLabel: "Equals",
    value: "SUCCESS",
    valueLabel: "Success",
})

const makeLeaf = (id: string, filters: AppliedFilter[] = []): LeafFilterGroup => ({
    id,
    kind: "leaf",
    filters,
})

const makeWrapper = (id: string, logical: "AND" | "OR" = "AND"): WrapperGroup => ({
    id,
    kind: "wrapper",
    logical,
    children: [makeLeaf(`${id}-child`)],
})

describe("useFilterGroups", () => {
    test("setTopLogical changes the operator and leaves state untouched when unchanged", () => {
        const tree = useFilterGroups()
        const groups = tree.groups.value

        expect(tree.topLogical.value).toBe("OR")

        tree.setTopLogical("AND")

        expect(tree.topLogical.value).toBe("AND")
        expect(tree.groups.value).toBe(groups)

        tree.setTopLogical("AND")

        expect(tree.topLogical.value).toBe("AND")
        expect(tree.groups.value).toBe(groups)
    })

    test("setWrapperLogical updates only the named wrapper", () => {
        const tree = useFilterGroups()
        const target = makeWrapper("target", "OR")
        const other = makeWrapper("other", "AND")
        const leaf = makeLeaf("top-level")
        tree.replaceTree([target, leaf, other])

        const [originalTarget, originalLeaf, originalOther] = tree.groups.value

        tree.setWrapperLogical("target", "AND")

        const groups = tree.groups.value
        expect(groups[0]).toMatchObject({id: "target", kind: "wrapper", logical: "AND"})
        expect(groups[0]).not.toBe(originalTarget)
        expect(groups[1]).toBe(originalLeaf)
        expect(groups[2]).toBe(originalOther)

        tree.setWrapperLogical("top-level", "OR")
        tree.setWrapperLogical("missing", "OR")
        expect(tree.groups.value[0]).toBe(groups[0])
        expect(tree.groups.value[1]).toBe(originalLeaf)
        expect(tree.groups.value[2]).toBe(originalOther)
    })

    test("setWrapperLogical keeps the group object when its operator is unchanged", () => {
        const tree = useFilterGroups()
        const wrapper = makeWrapper("wrapper", "AND")
        tree.replaceTree([wrapper])
        const originalWrapper = tree.groups.value[0]

        tree.setWrapperLogical("wrapper", "AND")

        expect(tree.groups.value[0]).toBe(originalWrapper)
    })

    test("updateLeaf applies a mutation to a top-level leaf", () => {
        const tree = useFilterGroups()
        const leaf = makeLeaf("top-level", [makeFilter("first")])
        tree.replaceTree([leaf])

        const updated = tree.updateLeaf("top-level", current => ({
            ...current,
            filters: [...current.filters, makeFilter("second")],
        }))

        expect(updated).toBe(true)
        expect((tree.groups.value[0] as LeafFilterGroup).filters.map(filter => filter.id))
            .toEqual(["first", "second"])
    })

    test("updateLeaf applies a mutation to a leaf inside a wrapper", () => {
        const tree = useFilterGroups()
        const sibling = makeLeaf("sibling", [makeFilter("unchanged")])
        const wrapper: WrapperGroup = {
            id: "wrapper",
            kind: "wrapper",
            logical: "AND",
            children: [makeLeaf("child", [makeFilter("first")]), sibling],
        }
        tree.replaceTree([wrapper])

        const updated = tree.updateLeaf("child", current => ({
            ...current,
            filters: [...current.filters, makeFilter("second")],
        }))

        expect(updated).toBe(true)
        const updatedWrapper = tree.groups.value[0] as WrapperGroup
        expect(updatedWrapper.children[0].filters.map(filter => filter.id))
            .toEqual(["first", "second"])
        expect(updatedWrapper.children[1]).toEqual(sibling)
    })

    test("updateLeaf returns false for an unknown leaf id", () => {
        const tree = useFilterGroups()
        const groups = tree.groups.value

        const updated = tree.updateLeaf("missing", leaf => leaf)

        expect(updated).toBe(false)
        expect(tree.groups.value).toEqual(groups)
    })

    test("replaceTree installs the given groups and top-level operator", () => {
        const tree = useFilterGroups()
        const next: FilterGroup[] = [makeLeaf("leaf", [makeFilter("filter")]), makeWrapper("wrapper")]

        tree.replaceTree(next, "AND")

        expect(tree.groups.value).toEqual(next)
        expect(tree.topLogical.value).toBe("AND")
    })

    test("replaceTree replaces an empty tree with one empty leaf", () => {
        const tree = useFilterGroups()

        tree.replaceTree([], "AND")

        expect(tree.groups.value).toHaveLength(1)
        expect(tree.groups.value[0]).toMatchObject({
            kind: "leaf",
            filters: [],
        })
        expect(tree.topLogical.value).toBe("AND")
    })

    test("replaceTree defaults the top-level operator to OR", () => {
        const tree = useFilterGroups()
        tree.setTopLogical("AND")

        tree.replaceTree([makeLeaf("replacement")])

        expect(tree.groups.value).toEqual([makeLeaf("replacement")])
        expect(tree.topLogical.value).toBe("OR")
    })

    test("clearTree resets to one empty leaf and OR", () => {
        const tree = useFilterGroups()
        tree.replaceTree([makeLeaf("leaf", [makeFilter("filter")]), makeWrapper("wrapper")], "AND")

        tree.clearTree()

        expect(tree.groups.value).toHaveLength(1)
        expect(tree.groups.value[0]).toMatchObject({
            kind: "leaf",
            filters: [],
        })
        expect(tree.topLogical.value).toBe("OR")
    })
})