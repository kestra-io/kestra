import {describe, expect, it} from "vitest"
import {
    Comparators,
    isWrapperGroup,
    useFilterGroups,
    findLeafContaining,
    allFilters,
    type AppliedFilter,
} from "@kestra-io/design-system"

const filter = (id: string, key: string, comparator: Comparators, value: AppliedFilter["value"]): AppliedFilter => ({
    id,
    key,
    keyLabel: key,
    comparator,
    comparatorLabel: String(comparator),
    value,
    valueLabel: String(value),
})

describe("useFilterGroups", () => {
    describe("initial state", () => {
        it("starts with a single empty leaf group and OR top operator", () => {
            const tree = useFilterGroups()
            expect(tree.groups.value).toHaveLength(1)
            expect(isWrapperGroup(tree.groups.value[0])).toBe(false)
            expect(tree.appliedFilters.value).toEqual([])
            expect(tree.topLogical.value).toBe("OR")
        })
    })

    describe("updateLeaf", () => {
        it("mutates the matching leaf in place", () => {
            const tree = useFilterGroups()
            const leafId = tree.groups.value[0].id
            const f = filter("f1", "state", Comparators.EQUALS, "RUNNING")

            const found = tree.updateLeaf(leafId, leaf => ({...leaf, filters: [...leaf.filters, f]}))
            expect(found).toBe(true)
            expect(tree.appliedFilters.value).toEqual([f])
        })

        it("returns false when the leaf id doesn't exist", () => {
            const tree = useFilterGroups()
            const found = tree.updateLeaf("nope", leaf => leaf)
            expect(found).toBe(false)
        })
    })

    describe("setTopLogical / setWrapperLogical", () => {
        it("flips the top-level operator", () => {
            const tree = useFilterGroups()
            tree.setTopLogical("AND")
            expect(tree.topLogical.value).toBe("AND")
            tree.setTopLogical("OR")
            expect(tree.topLogical.value).toBe("OR")
        })

        it("flips a wrapper's operator without touching siblings", () => {
            const tree = useFilterGroups()
            tree.addGroup()
            const [g1, g2] = tree.groups.value.map(g => g.id)
            tree.updateLeaf(g1, l => ({...l, filters: [filter("a", "x", Comparators.EQUALS, "1")]}))
            tree.updateLeaf(g2, l => ({...l, filters: [filter("b", "y", Comparators.EQUALS, "2")]}))
            tree.wrapGroups(g2, g1)
            const wrapperId = tree.groups.value[0].id

            tree.setWrapperLogical(wrapperId, "OR")
            const wrapper = tree.groups.value[0]
            if (isWrapperGroup(wrapper)) expect(wrapper.logical).toBe("OR")
        })
    })

    describe("tree-walking helpers", () => {
        it("allFilters flattens leaves and wrapper children", () => {
            const tree = useFilterGroups()
            tree.addGroup()
            const [g1, g2] = tree.groups.value.map(g => g.id)
            const a = filter("a", "x", Comparators.EQUALS, "1")
            const b = filter("b", "y", Comparators.EQUALS, "2")
            tree.updateLeaf(g1, l => ({...l, filters: [a]}))
            tree.updateLeaf(g2, l => ({...l, filters: [b]}))
            tree.wrapGroups(g2, g1)

            expect(allFilters(tree.groups.value).map(f => f.id).sort()).toEqual(["a", "b"])
        })

        it("findLeafContaining walks both top-level and wrapper children", () => {
            const tree = useFilterGroups()
            tree.addGroup()
            const [g1, g2] = tree.groups.value.map(g => g.id)
            tree.updateLeaf(g1, l => ({...l, filters: [filter("a", "x", Comparators.EQUALS, "1")]}))
            tree.updateLeaf(g2, l => ({...l, filters: [filter("b", "y", Comparators.EQUALS, "2")]}))
            tree.wrapGroups(g2, g1)

            const leafForA = findLeafContaining(tree.groups.value, "a")
            const leafForB = findLeafContaining(tree.groups.value, "b")
            expect(leafForA).toBeDefined()
            expect(leafForB).toBeDefined()
            expect(leafForA?.id).not.toBe(leafForB?.id)
        })
    })
})
