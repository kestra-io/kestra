import {describe, test, expect} from "vitest"
import {
    Comparators,
    isWrapperGroup,
    type AppliedFilter,
    type FilterGroup,
    type LeafFilterGroup,
    type WrapperGroup,
} from "../utils/filterTypes"
import {useFilterGroups} from "./useFilterGroups"

const makeFilter = (overrides: Partial<AppliedFilter> = {}): AppliedFilter => ({
    id: "f1",
    key: "namespace",
    keyLabel: "Namespace",
    comparator: Comparators.EQUALS,
    comparatorLabel: "Equals",
    value: "company.team",
    valueLabel: "company.team",
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

const setup = (groups: FilterGroup[]) => {
    const tree = useFilterGroups()
    tree.replaceTree(groups)
    return tree
}

const idsOf = (leaf: FilterGroup) =>
    isWrapperGroup(leaf) ? [] : leaf.filters.map(f => f.id)

const wrapperAt = (tree: ReturnType<typeof setup>, index: number): WrapperGroup => {
    const group = tree.groups.value[index]
    if (!isWrapperGroup(group)) {
        throw new Error(`Expected group ${index} to be a wrapper but it is a leaf.`)
    }
    return group
}

describe("useFilterGroups - moveFilter", () => {
    test("appends the filter to a target leaf without a conflicting field", () => {
        // Given
        const tree = setup([
            makeLeaf("source", [makeFilter({id: "f1"})]),
            makeLeaf("target"),
        ])

        // When
        tree.moveFilter("f1", "target")

        // Then
        expect(tree.groups.value).toHaveLength(2)
        expect(idsOf(tree.groups.value[0])).toEqual([])
        expect(idsOf(tree.groups.value[1])).toEqual(["f1"])
    })

    test("does not treat the same key with another comparator as a conflict", () => {
        // Given
        const tree = setup([
            makeLeaf("source", [makeFilter({id: "f1", comparator: Comparators.EQUALS})]),
            makeLeaf("target", [makeFilter({id: "f2", comparator: Comparators.CONTAINS})]),
        ])

        // When
        tree.moveFilter("f1", "target")

        // Then
        expect(tree.groups.value).toHaveLength(2)
        expect(idsOf(tree.groups.value[1])).toEqual(["f2", "f1"])
    })

    test("does nothing when the source and target are the same leaf", () => {
        // Given
        const build = () => [makeLeaf("only", [makeFilter({id: "f1"}), makeFilter({id: "f2", key: "flowId"})])]
        const tree = setup(build())

        // When
        tree.moveFilter("f1", "only")

        // Then
        expect(tree.groups.value).toEqual(build())
    })

    test.each([
        ["filter", "unknown", "target"],
        ["target leaf", "f1", "unknown"],
    ])("does nothing when the %s cannot be found", (_, filterId, targetId) => {
        // Given
        const build = () => [
            makeLeaf("source", [makeFilter({id: "f1"})]),
            makeLeaf("target", [makeFilter({id: "f2", key: "flowId"})]),
        ]
        const tree = setup(build())

        // When
        tree.moveFilter(filterId, targetId)

        // Then
        expect(tree.groups.value).toEqual(build())
    })

    test("turns a top-level target leaf into a wrapper when its field conflicts", () => {
        // Given: the target already holds a "namespace" filter and an unrelated "flowId" one
        const tree = setup([
            makeLeaf("source", [makeFilter({id: "f1"})]),
            makeLeaf("target", [
                makeFilter({id: "f2", value: "other.team"}),
                makeFilter({id: "f3", key: "flowId"}),
            ]),
        ])

        // When
        tree.moveFilter("f1", "target")

        // Then: the unrelated filter, the conflicting one and the moved one each get their own leaf
        expect(tree.groups.value).toHaveLength(2)
        expect(idsOf(tree.groups.value[0])).toEqual([])

        const wrapper = wrapperAt(tree, 1)
        expect(wrapper.logical).toBe("AND")
        expect(wrapper.children.map(idsOf)).toEqual([["f3"], ["f2"], ["f1"]])
        expect(tree.appliedFilters.value.map(f => f.id).sort()).toEqual(["f1", "f2", "f3"])
    })

    test("adds a child leaf instead of rebuilding a wrapper that already contains the target", () => {
        // Given
        const tree = setup([
            makeLeaf("source", [makeFilter({id: "f1"})]),
            makeWrapper("wrapper", [
                makeLeaf("conflicting", [makeFilter({id: "f2", value: "other.team"})]),
                makeLeaf("unrelated", [makeFilter({id: "f3", key: "flowId"})]),
            ]),
        ])

        // When
        tree.moveFilter("f1", "conflicting")

        // Then
        expect(tree.groups.value).toHaveLength(2)

        const wrapper = wrapperAt(tree, 1)
        expect(wrapper.id).toBe("wrapper")
        expect(wrapper.children.map(c => c.id).slice(0, 2)).toEqual(["conflicting", "unrelated"])
        expect(wrapper.children.map(idsOf)).toEqual([["f2"], ["f3"], ["f1"]])
    })
})

describe("useFilterGroups - placeFilter", () => {
    const threeFilters = () => makeLeaf("leaf", [
        makeFilter({id: "f1", key: "namespace"}),
        makeFilter({id: "f2", key: "flowId"}),
        makeFilter({id: "f3", key: "state"}),
    ])

    test.each([
        ["to the front", "f3", 0, ["f3", "f1", "f2"]],
        ["to the end", "f1", 2, ["f2", "f3", "f1"]],
    ])("reorders a filter %s of its own leaf", (_, filterId, index, expected) => {
        // Given
        const tree = setup([threeFilters()])

        // When
        tree.placeFilter(filterId, "leaf", index)

        // Then
        expect(idsOf(tree.groups.value[0])).toEqual(expected)
    })

    test("moves a filter to another leaf and then positions it at the requested index", () => {
        // Given
        const tree = setup([
            makeLeaf("source", [makeFilter({id: "f1", key: "namespace"})]),
            makeLeaf("target", [
                makeFilter({id: "f2", key: "flowId"}),
                makeFilter({id: "f3", key: "state"}),
            ]),
        ])

        // When
        tree.placeFilter("f1", "target", 1)

        // Then
        expect(idsOf(tree.groups.value[0])).toEqual([])
        expect(idsOf(tree.groups.value[1])).toEqual(["f2", "f1", "f3"])
    })

    test.each([
        ["below zero", "f3", -1, ["f3", "f1", "f2"]],
        ["past the end", "f1", 99, ["f2", "f3", "f1"]],
    ])("clamps an index %s instead of throwing", (_, filterId, index, expected) => {
        // Given
        const tree = setup([threeFilters()])

        // When
        tree.placeFilter(filterId, "leaf", index)

        // Then
        expect(idsOf(tree.groups.value[0])).toEqual(expected)
    })
})
