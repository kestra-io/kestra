import {beforeEach, describe, expect, it, vi} from "vitest"

const posthogEvents = vi.fn()

vi.mock("../../stores/api", () => ({
    useApiStore: () => ({posthogEvents}),
}))

describe("taskEditorEvents", () => {
    beforeEach(() => posthogEvents.mockClear())

    it("tracks a context section expansion", async () => {
        const {trackContextSectionExpanded} = await import("./taskEditorEvents")
        trackContextSectionExpanded("inputs.namespaceKv")
        expect(posthogEvents).toHaveBeenCalledWith({type: "CONTEXT_SECTION_EXPANDED", section: "inputs.namespaceKv"})
    })

    it("tracks a chip insertion, distinct from a copy, by section — never by chip content", async () => {
        const {trackChipInserted, trackChipCopied} = await import("./taskEditorEvents")
        trackChipInserted("inputs.namespaceKv")
        trackChipCopied("inputs.namespaceSecrets")
        expect(posthogEvents).toHaveBeenCalledWith({type: "CHIP_INSERTED", section: "inputs.namespaceKv"})
        expect(posthogEvents).toHaveBeenCalledWith({type: "CHIP_COPIED", section: "inputs.namespaceSecrets"})

        const payloads = posthogEvents.mock.calls.map(([payload]) => JSON.stringify(payload))
        const leaksChipContent = payloads.some(payload => /kv\(|secret\(|read\(|fileURI\(|\{\{/.test(payload))
        expect(leaksChipContent).toBe(false)
    })

    it("tracks a jump to the first unset required field", async () => {
        const {trackRequiredFieldJump} = await import("./taskEditorEvents")
        trackRequiredFieldJump("data.query")
        expect(posthogEvents).toHaveBeenCalledWith({type: "REQUIRED_FIELD_JUMP", field: "data.query"})
    })
})
