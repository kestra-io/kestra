import {describe, it, expect, beforeEach, vi} from "vitest"

import {useYamlUndo} from "../../../../../src/components/no-code/blocks/useYamlUndo"

function makeStore(namespace = "company.team", id = "flow_a") {
    return {
        flowYaml: "tasks: []" as string | undefined,
        flow: {namespace, id},
        onEdit: vi.fn(),
    }
}

const label = (name: string) => `deleted ${name}`

describe("useYamlUndo", () => {
    beforeEach(() => {
        // The history outlives any one surface, so each case starts from a flow of its own.
        const reset = useYamlUndo(makeStore("reset", `${Math.random()}`), label)
        reset.applyYaml("reset: true")
        reset.performUndo()
    })

    it("undoes an edit made from another surface on the same flow", () => {
        const store = makeStore()
        const topology = useYamlUndo(store, label)
        const noCode = useYamlUndo(store, label)

        topology.applyYaml("tasks: [a]")
        expect(store.flowYaml).toBe("tasks: [a]")

        expect(noCode.performUndo()).toBe(true)
        expect(store.flowYaml).toBe("tasks: []")
    })

    it("reports an empty history so the shortcut leaves the browser's own undo alone", () => {
        const noCode = useYamlUndo(makeStore(), label)

        expect(noCode.performUndo()).toBe(false)
    })

    it("drops the history when the flow changes, so an undo cannot cross flows", () => {
        const first = makeStore("company.team", "flow_a")
        const surface = useYamlUndo(first, label)
        surface.applyYaml("tasks: [a]")

        const second = makeStore("company.team", "flow_b")
        const other = useYamlUndo(second, label)

        expect(other.performUndo()).toBe(false)
        expect(second.flowYaml).toBe("tasks: []")
    })
})
