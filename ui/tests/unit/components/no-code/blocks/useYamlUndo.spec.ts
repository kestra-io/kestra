import {describe, it, expect, beforeEach, vi} from "vitest"
import {nextTick, reactive} from "vue"

import {useYamlUndo} from "../../../../../src/components/no-code/blocks/useYamlUndo"

function makeStore(namespace = "company.team", id = "flow_a") {
    return {
        flowYaml: "tasks: []" as string | undefined,
        flow: {namespace, id},
        onEdit: vi.fn(),
    }
}

// The bypass-write detection reacts to flowStore.flowYaml through a Vue `watch`, which only fires
// on a reactive source — the plain object `makeStore()` returns is not one.
function makeReactiveStore(namespace = "company.team", id = "flow_a") {
    return reactive(makeStore(namespace, id))
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

    it("clears the delete toast when another surface edits, so it cannot undo a different change", () => {
        const store = makeStore()
        const noCode = useYamlUndo(store, label)
        const topology = useYamlUndo(store, label)

        noCode.deleteWithUndo("log_task", () => noCode.applyYaml("tasks: []"))
        expect(noCode.undoState.value).toEqual({label: "deleted log_task"})

        topology.applyYaml("tasks: [a]")

        expect(noCode.undoState.value).toBeNull()
        expect(topology.undoState.value).toBeNull()
    })

    it("shows the delete toast on every surface, not only the one that deleted", () => {
        const store = makeStore()
        const noCode = useYamlUndo(store, label)
        const topology = useYamlUndo(store, label)

        noCode.deleteWithUndo("log_task", () => noCode.applyYaml("tasks: []"))

        expect(topology.undoState.value).toEqual({label: "deleted log_task"})
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

    it("redo restores exactly the edit undo just took back", () => {
        const store = makeStore()
        const surface = useYamlUndo(store, label)

        surface.applyYaml("tasks: [a]")
        surface.applyYaml("tasks: [a, b]")

        expect(surface.performUndo()).toBe(true)
        expect(store.flowYaml).toBe("tasks: [a]")

        expect(surface.performRedo()).toBe(true)
        expect(store.flowYaml).toBe("tasks: [a, b]")
    })

    it("reports an empty redo history so the shortcut leaves the browser's own redo alone", () => {
        const surface = useYamlUndo(makeStore(), label)

        expect(surface.performRedo()).toBe(false)
    })

    it("clears the redo history on a real edit, so redo cannot resurrect an abandoned branch", () => {
        const store = makeStore()
        const surface = useYamlUndo(store, label)

        surface.applyYaml("tasks: [a]")
        surface.performUndo()

        surface.applyYaml("tasks: [c]")

        expect(surface.performRedo()).toBe(false)
        expect(store.flowYaml).toBe("tasks: [c]")
    })

    it("clears the redo history when something writes flowYaml outside applyYaml, e.g. the Code panel", async () => {
        const store = makeReactiveStore()
        const surface = useYamlUndo(store, label)

        surface.applyYaml("tasks: [a]")
        surface.performUndo()
        await nextTick()

        // A direct write, bypassing applyYaml — this is what FlowFileEditorTab.vue's
        // editorUpdate does on every keystroke in the Code panel.
        store.flowYaml = "tasks: [typed in the code panel]"
        await nextTick()

        // Then — redo must not resurrect the pre-keystroke state over what was just typed
        expect(surface.performRedo()).toBe(false)
        expect(store.flowYaml).toBe("tasks: [typed in the code panel]")
    })

    it("does not clear its own redo history when applyYaml/performUndo write flowYaml", async () => {
        const store = makeReactiveStore()
        const surface = useYamlUndo(store, label)

        surface.applyYaml("tasks: [a]")
        surface.applyYaml("tasks: [a, b]")
        surface.performUndo()
        await nextTick()

        expect(surface.performRedo()).toBe(true)
        expect(store.flowYaml).toBe("tasks: [a, b]")
    })

    it("caps the undo history at 100 entries, so at most that many edits are recoverable", () => {
        const store = makeStore()
        const surface = useYamlUndo(store, label)

        for (let i = 0; i < 105; i++) surface.applyYaml(`tasks: [v${i}]`)

        let undone = 0
        while (surface.performUndo()) undone++

        expect(undone).toBe(100)
        // The oldest 5 edits (the scope's initial "tasks: []" plus v0-v3) fell off the cap.
        expect(store.flowYaml).toBe("tasks: [v4]")
    })
})
