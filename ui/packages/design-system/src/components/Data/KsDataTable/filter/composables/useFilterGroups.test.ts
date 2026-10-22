
import {describe, test, expect} from "vitest"
import {useFilterGroups} from "./useFilterGroups"

const leaf = (id: string) => ({
    id,
    kind: "leaf" as const,
    filters: [],
})

const wrapper = (id: string, children: ReturnType<typeof leaf>[]) => ({
    id,
    kind: "wrapper" as const,
    logical: "AND" as const,
    children,
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
        tree.replaceTree([leaf("a"), leaf("b"), leaf("c")])

        tree.removeGroup("b")

        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "c"])
    })

    test("removeGroup refuses to remove the last remaining group", () => {
        const tree = useFilterGroups()
        tree.replaceTree([leaf("a")])

        tree.removeGroup("a")

        expect(tree.groups.value.map(group => group.id)).toEqual(["a"])
    })

    test("removeGroup leaves the tree unchanged for an unknown id", () => {
        const tree = useFilterGroups()
        tree.replaceTree([leaf("a"), leaf("b")])

        tree.removeGroup("unknown")

        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "b"])
    })

    test("wrapGroups combines two leaves in an AND wrapper", () => {
        const tree = useFilterGroups()
        tree.replaceTree([leaf("source"), leaf("target")])

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
            leaf("source"),
            wrapper("target", [leaf("a"), leaf("b")]),
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
        tree.replaceTree([leaf("a"), leaf("b")])

        tree.wrapGroups("a", "a")

        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "b"])
    })

    test("wrapGroups is a no-op when either id is unknown", () => {
        const tree = useFilterGroups()
        tree.replaceTree([leaf("a"), leaf("b")])

        tree.wrapGroups("missing", "b")
        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "b"])

        tree.wrapGroups("a", "missing")
        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "b"])
    })

    test("wrapGroups is a no-op when the source is a wrapper", () => {
        const tree = useFilterGroups()
        tree.replaceTree([
            wrapper("source", [leaf("child")]),
            leaf("target"),
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
            leaf("before"),
            wrapper("group", [leaf("a"), leaf("b")]),
            leaf("after"),
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
        tree.replaceTree([leaf("a"), leaf("b")])

        tree.unwrapGroup("a")

        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "b"])
    })

    test("unwrapGroup is a no-op for an unknown id", () => {
        const tree = useFilterGroups()
        tree.replaceTree([leaf("a"), wrapper("group", [leaf("b")])])

        tree.unwrapGroup("unknown")

        expect(tree.groups.value.map(group => group.id)).toEqual(["a", "group"])
    })
})
