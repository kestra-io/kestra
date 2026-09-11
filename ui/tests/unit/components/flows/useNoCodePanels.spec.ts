import {ref} from "vue"
import {describe, it, expect, vi} from "vitest"
import {useNoCodeHandlers, getCreateTabKey} from "../../../../src/components/flows/useNoCodePanels"

describe("useNoCodeHandlers.onCreateTask", () => {
    it("focuses the existing create-tab instead of opening a duplicate on repeated calls", () => {
        const openTabs = ref<string[]>([])
        const focusTab = vi.fn()
        let counter = 0
        // Mirrors openAddTaskTab's own "after" default exactly - the mock must apply it
        // independently, not just forward whatever it was given, or a mismatch between
        // onCreateTask's dedup key and the tab actually created (like the one that shipped
        // in #18321: the dedup check omitted the defaulted position) would go undetected.
        const actions = {
            openAddTaskTab: vi.fn((_opener, parentPath, blockSchemaPath, refPath, position = "after") => {
                openTabs.value = [...openTabs.value, getCreateTabKey({parentPath, refPath, blockSchemaPath, position} as any, counter++)]
            }),
            openEditTaskTab: vi.fn(),
        } as any

        const handlers = useNoCodeHandlers(openTabs, focusTab, actions)
        const opener = {panelIndex: 0, tabIndex: 0}

        handlers.onCreateTask(opener, "triggers", "schema/path")
        expect(actions.openAddTaskTab).toHaveBeenCalledTimes(1)
        expect(openTabs.value).toHaveLength(1)

        // Repeated call with the same parentPath/blockSchemaPath, and no explicit position
        // (matching the createTrigger route-query handler's call), must focus the already-open
        // tab instead of opening a duplicate.
        handlers.onCreateTask(opener, "triggers", "schema/path")
        expect(actions.openAddTaskTab).toHaveBeenCalledTimes(1)
        expect(openTabs.value).toHaveLength(1)
        expect(focusTab).toHaveBeenCalledWith(openTabs.value[0])
    })

    it("opens a distinct tab for a different parentPath", () => {
        const openTabs = ref<string[]>([])
        const focusTab = vi.fn()
        let counter = 0
        const actions = {
            openAddTaskTab: vi.fn((_opener, parentPath, blockSchemaPath, refPath, position = "after") => {
                openTabs.value = [...openTabs.value, getCreateTabKey({parentPath, refPath, blockSchemaPath, position} as any, counter++)]
            }),
            openEditTaskTab: vi.fn(),
        } as any

        const handlers = useNoCodeHandlers(openTabs, focusTab, actions)
        const opener = {panelIndex: 0, tabIndex: 0}

        handlers.onCreateTask(opener, "triggers", "schema/path")
        handlers.onCreateTask(opener, "tasks", "schema/other-path")

        expect(actions.openAddTaskTab).toHaveBeenCalledTimes(2)
        expect(openTabs.value).toHaveLength(2)
        expect(focusTab).not.toHaveBeenCalled()
    })
})
