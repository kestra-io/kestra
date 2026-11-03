import {describe, expect, test} from "vitest"
import {Comparators, type AppliedFilter, type FilterGroup, type LeafFilterGroup, type WrapperGroup} from "../utils/filterTypes"
import {allFilters, findLeafById, findLeafContaining, useFilterGroups} from "./useFilterGroups"

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

const makeWrapper = (
    id: string,
    logical: "AND" | "OR" = "AND",
    children: LeafFilterGroup[] = [makeLeaf(`${id}-child`)],
): WrapperGroup => ({
    id,
    kind: "wrapper",
    logical,
    children,
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

describe("useFilterGroups - group structure operations", () => {
    test("addGroup appends a new empty leaf", () => {
        const tree = useFilterGroups()
        const initialId = tree.groups.value[0].id

        tree.addGroup()

        expect(tree.groups.value).toHaveLength(2)
        expect(tree.groups.value[0].id).toBe(initialId)
        expect(tree.groups.value[1]).toMatchObject({
            kind: "leaf",
            filters: [],
        })
        expect(tree.groups.value[1].id).not.toBe(initialId)
    })

    test("removeGroup removes the named group", () => {
        const tree = useFilterGroups()
        tree.replaceTree([makeLeaf("a"), makeLeaf("b"), makeLeaf("c")])

        tree.removeGroup("b")

        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "c"])
    })

    test("removeGroup refuses to remove the last remaining group", () => {
        const tree = useFilterGroups()
        tree.replaceTree([makeLeaf("a")])

        tree.removeGroup("a")

        expect(tree.groups.value.map(group => group.id)).toEqual(["a"])
    })

    test("removeGroup leaves the tree unchanged for an unknown id", () => {
        const tree = useFilterGroups()
        tree.replaceTree([makeLeaf("a"), makeLeaf("b")])

        tree.removeGroup("unknown")

        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "b"])
    })

    test("wrapGroups combines two leaves in an AND wrapper", () => {
        const tree = useFilterGroups()
        tree.replaceTree([makeLeaf("source"), makeLeaf("target")])

        tree.wrapGroups("source", "target")

        expect(tree.groups.value).toHaveLength(1)
        expect(tree.groups.value[0]).toMatchObject({
            kind: "wrapper",
            logical: "AND",
            children: [
                {id: "target", kind: "leaf"},
                {id: "source", kind: "leaf"},
            ],
        })
    })

    test("wrapGroups appends the source to an existing wrapper", () => {
        const tree = useFilterGroups()
        tree.replaceTree([
            makeLeaf("source"),
            makeWrapper("target", "AND", [makeLeaf("a"), makeLeaf("b")]),
        ])

        tree.wrapGroups("source", "target")

        expect(tree.groups.value).toHaveLength(1)
        expect(tree.groups.value[0]).toMatchObject({
            id: "target",
            kind: "wrapper",
            logical: "AND",
            children: [
                {id: "a"},
                {id: "b"},
                {id: "source"},
            ],
        })
    })

    test("wrapGroups is a no-op when source and target are the same", () => {
        const tree = useFilterGroups()
        tree.replaceTree([makeLeaf("a"), makeLeaf("b")])

        tree.wrapGroups("a", "a")

        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "b"])
    })

    test("wrapGroups is a no-op when either id is unknown", () => {
        const tree = useFilterGroups()
        tree.replaceTree([makeLeaf("a"), makeLeaf("b")])

        tree.wrapGroups("missing", "b")
        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "b"])

        tree.wrapGroups("a", "missing")
        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "b"])
    })

    test("wrapGroups is a no-op when the source is a wrapper", () => {
        const tree = useFilterGroups()
        tree.replaceTree([
            makeWrapper("source", "AND", [makeLeaf("child")]),
            makeLeaf("target"),
        ])

        tree.wrapGroups("source", "target")

        expect(tree.groups.value.map(group => group.id)).toEqual([
            "source",
            "target",
        ])
    })

    test("unwrapGroup restores children in their original position and order", () => {
        const tree = useFilterGroups()
        tree.replaceTree([
            makeLeaf("before"),
            makeWrapper("group", "AND", [makeLeaf("a"), makeLeaf("b")]),
            makeLeaf("after"),
        ])

        tree.unwrapGroup("group")

        expect(tree.groups.value.map(group => group.id)).toEqual([
            "before",
            "a",
            "b",
            "after",
        ])
    })

    test("unwrapGroup is a no-op for a leaf id", () => {
        const tree = useFilterGroups()
        tree.replaceTree([makeLeaf("a"), makeLeaf("b")])

        tree.unwrapGroup("a")

        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "b"])
    })

    test("unwrapGroup is a no-op for an unknown id", () => {
        const tree = useFilterGroups()
        tree.replaceTree([makeLeaf("a"), makeWrapper("group", "AND", [makeLeaf("b")])])

        tree.unwrapGroup("unknown")

        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "group"])
    })
})

describe("useFilterGroups - tree helpers", () => {
    test("allFilters keeps tree order and returns nothing for an empty tree", () => {
        const groups: FilterGroup[] = [
            makeLeaf("leaf", [makeFilter("first")]),
            makeWrapper("wrapper", "AND", [makeLeaf("child", [makeFilter("second")])]),
        ]

        expect(allFilters(groups).map(filter => filter.id)).toEqual(["first", "second"])
        expect(allFilters([])).toEqual([])
    })

    test("findLeafContaining returns undefined for an unknown filter id", () => {
        expect(findLeafContaining([makeLeaf("leaf", [makeFilter("first")])], "missing")).toBeUndefined()
    })

    test("findLeafById finds top-level and wrapped leaves but not wrappers", () => {
        const top = makeLeaf("top")
        const child = makeLeaf("child")
        const groups: FilterGroup[] = [top, makeWrapper("wrapper", "AND", [child])]

        expect(findLeafById(groups, "top")).toBe(top)
        expect(findLeafById(groups, "child")).toBe(child)
        expect(findLeafById(groups, "wrapper")).toBeUndefined()
        expect(findLeafById(groups, "missing")).toBeUndefined()
    })
})
