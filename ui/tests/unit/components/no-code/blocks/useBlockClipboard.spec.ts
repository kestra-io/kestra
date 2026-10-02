import {describe, it, expect, vi} from "vitest"

import {useBlockClipboard} from "../../../../../src/components/no-code/blocks/useBlockClipboard"

// The clipboard ref is module-scope, shared by every surface and every case below, so each test
// copies its own fixture first rather than relying on a starting-empty clipboard.
describe("useBlockClipboard", () => {
    it("refuses a cross-section paste of a task into triggers", () => {
        // Given
        const {copy, canPasteInto} = useBlockClipboard()
        copy("tasks", {id: "log", type: "io.kestra.plugin.core.log.Log"})

        // When / Then
        expect(canPasteInto("triggers")).toBe(false)
        expect(canPasteInto("tasks")).toBe(true)
    })

    it("refuses a cross-section paste of a trigger into tasks", () => {
        // Given
        const {copy, canPasteInto} = useBlockClipboard()
        copy("triggers", {id: "webhook", type: "io.kestra.plugin.core.trigger.Webhook"})

        // When / Then
        expect(canPasteInto("tasks")).toBe(false)
        expect(canPasteInto("triggers")).toBe(true)
    })

    it("allows a task copied from one task-holding section to paste into another", () => {
        // Given — errors/finally/afterExecution are all "task" sections, unlike triggers
        const {copy, canPasteInto} = useBlockClipboard()
        copy("tasks", {id: "log", type: "io.kestra.plugin.core.log.Log"})

        // When / Then
        expect(canPasteInto("errors")).toBe(true)
        expect(canPasteInto("finally")).toBe(true)
        expect(canPasteInto("afterExecution")).toBe(true)
    })

    it("returns no block from pasteFor when the section kind does not match", () => {
        // Given
        const {copy, pasteFor} = useBlockClipboard()
        copy("tasks", {id: "log", type: "io.kestra.plugin.core.log.Log"})

        // When
        const result = pasteFor("triggers", new Set())

        // Then
        expect(result).toBeUndefined()
    })

    it("renames the pasted block's id when it collides with the destination", () => {
        // Given
        const {copy, pasteFor} = useBlockClipboard()
        copy("tasks", {id: "log", type: "io.kestra.plugin.core.log.Log"})

        // When
        const result = pasteFor("tasks", new Set(["log"]))

        // Then
        expect(result).toBeDefined()
        expect(result?.id).not.toBe("log")
    })

    it("keeps the pasted block's id when it does not collide with the destination", () => {
        // Given
        const {copy, pasteFor} = useBlockClipboard()
        copy("tasks", {id: "log", type: "io.kestra.plugin.core.log.Log"})

        // When
        const result = pasteFor("tasks", new Set(["unrelated_task"]))

        // Then
        expect(result?.id).toBe("log")
    })

    it("mirrors a copy to the system clipboard as YAML, write-only", async () => {
        // Given
        const writeText = vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue(undefined)
        const {copy} = useBlockClipboard()

        // When
        copy("tasks", {id: "log", type: "io.kestra.plugin.core.log.Log"})
        await Promise.resolve()

        // Then
        expect(writeText).toHaveBeenCalledWith(expect.stringContaining("id: log"))
        writeText.mockRestore()
    })
})
