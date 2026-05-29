import {describe, expect, it} from "vitest"
import {
    Comparators,
    isWrapperGroup,
    useFilterGroups,
    findLeafById,
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

    describe("addGroup / removeGroup", () => {
        it("appends a new empty leaf group", () => {
            const tree = useFilterGroups()
            tree.addGroup()
            expect(tree.groups.value).toHaveLength(2)
        })

        it("removes a group by id but always preserves at least one", () => {
            const tree = useFilterGroups()
            const firstId = tree.groups.value[0].id
            tree.addGroup()
            const secondId = tree.groups.value[1].id

            tree.removeGroup(secondId)
            expect(tree.groups.value).toHaveLength(1)
            expect(tree.groups.value[0].id).toBe(firstId)

            // can't remove the last group
            tree.removeGroup(firstId)
            expect(tree.groups.value).toHaveLength(1)
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

    describe("wrapGroups", () => {
        it("combines two top-level leaves into an AND-wrapper at the target's position", () => {
            const tree = useFilterGroups()
            const g1 = tree.groups.value[0].id
            tree.addGroup()
            const g2 = tree.groups.value[1].id

            tree.updateLeaf(g1, leaf => ({...leaf, filters: [filter("f1", "state", Comparators.EQUALS, "A")]}))
            tree.updateLeaf(g2, leaf => ({...leaf, filters: [filter("f2", "namespace", Comparators.EQUALS, "ns")]}))

            tree.wrapGroups(g2, g1)
            expect(tree.groups.value).toHaveLength(1)
            const wrapped = tree.groups.value[0]
            expect(isWrapperGroup(wrapped)).toBe(true)
            if (isWrapperGroup(wrapped)) {
                expect(wrapped.logical).toBe("AND")
                expect(wrapped.children).toHaveLength(2)
            }
        })

        it("appends source to an existing wrapper as a new child", () => {
            const tree = useFilterGroups()
            const g1 = tree.groups.value[0].id
            tree.addGroup()
            const g2 = tree.groups.value[1].id
            tree.addGroup()
            const g3 = tree.groups.value[2].id

            tree.updateLeaf(g1, l => ({...l, filters: [filter("f1", "a", Comparators.EQUALS, "1")]}))
            tree.updateLeaf(g2, l => ({...l, filters: [filter("f2", "b", Comparators.EQUALS, "2")]}))
            tree.updateLeaf(g3, l => ({...l, filters: [filter("f3", "c", Comparators.EQUALS, "3")]}))

            tree.wrapGroups(g2, g1) // wrap g1+g2
            const wrapperId = tree.groups.value[0].id
            tree.wrapGroups(g3, wrapperId) // append g3 to the wrapper

            const wrapper = tree.groups.value[0]
            expect(tree.groups.value).toHaveLength(1)
            expect(isWrapperGroup(wrapper)).toBe(true)
            if (isWrapperGroup(wrapper)) {
                expect(wrapper.children).toHaveLength(3)
            }
        })

        it("does nothing when source and target are the same id", () => {
            const tree = useFilterGroups()
            const before = tree.groups.value
            tree.wrapGroups(before[0].id, before[0].id)
            expect(tree.groups.value).toBe(before)
        })

        it("does nothing when source is a wrapper (one-level-only)", () => {
            const tree = useFilterGroups()
            tree.addGroup(); tree.addGroup()
            const [g1, g2, g3] = tree.groups.value.map(g => g.id)
            tree.updateLeaf(g1, l => ({...l, filters: [filter("a", "x", Comparators.EQUALS, "1")]}))
            tree.updateLeaf(g2, l => ({...l, filters: [filter("b", "y", Comparators.EQUALS, "2")]}))
            tree.updateLeaf(g3, l => ({...l, filters: [filter("c", "z", Comparators.EQUALS, "3")]}))
            tree.wrapGroups(g2, g1)
            const wrapperId = tree.groups.value[0].id

            // dragging the wrapper itself onto g3 should be a no-op
            const before = tree.groups.value
            tree.wrapGroups(wrapperId, g3)
            expect(tree.groups.value).toBe(before)
        })
    })

    describe("unwrapGroup", () => {
        it("restores wrapper children as top-level siblings at the wrapper's position", () => {
            const tree = useFilterGroups()
            tree.addGroup()
            const [g1, g2] = tree.groups.value.map(g => g.id)
            tree.updateLeaf(g1, l => ({...l, filters: [filter("a", "x", Comparators.EQUALS, "1")]}))
            tree.updateLeaf(g2, l => ({...l, filters: [filter("b", "y", Comparators.EQUALS, "2")]}))
            tree.wrapGroups(g2, g1)

            const wrapperId = tree.groups.value[0].id
            tree.unwrapGroup(wrapperId)

            expect(tree.groups.value).toHaveLength(2)
            expect(tree.groups.value.every(g => !isWrapperGroup(g))).toBe(true)
        })
    })

    describe("moveFilter", () => {
        it("moves a filter between top-level leaves", () => {
            const tree = useFilterGroups()
            tree.addGroup()
            const [g1, g2] = tree.groups.value.map(g => g.id)
            const f = filter("f1", "state", Comparators.EQUALS, "RUNNING")
            tree.updateLeaf(g1, l => ({...l, filters: [f]}))

            tree.moveFilter("f1", g2)

            const sourceLeaf = findLeafById(tree.groups.value, g1)
            const targetLeaf = findLeafById(tree.groups.value, g2)
            expect(sourceLeaf?.filters).toHaveLength(0)
            expect(targetLeaf?.filters).toEqual([f])
        })

        it("displaces a target filter only when key AND comparator collide", () => {
            const tree = useFilterGroups()
            tree.addGroup()
            const [g1, g2] = tree.groups.value.map(g => g.id)
            const moving = filter("moving", "startDate", Comparators.GREATER_THAN, "X")
            const survivor = filter("survivor", "startDate", Comparators.LESS_THAN, "Y")
            tree.updateLeaf(g1, l => ({...l, filters: [moving]}))
            tree.updateLeaf(g2, l => ({...l, filters: [survivor]}))

            tree.moveFilter("moving", g2)

            const targetLeaf = findLeafById(tree.groups.value, g2)!
            expect(targetLeaf.filters.map(f => f.id).sort()).toEqual(["moving", "survivor"])
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
