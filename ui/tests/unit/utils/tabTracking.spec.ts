import {describe, it, expect, vi, beforeEach} from "vitest"

const eventsMock = vi.fn()
const posthogEventsMock = vi.fn()
const mockConfigs: {isAnonymousUsageEnabled?: boolean; uuid?: string} = {isAnonymousUsageEnabled: true, uuid: "test-uuid"}

vi.mock("../../../src/stores/api", () => ({
    useApiStore: () => ({events: eventsMock, posthogEvents: posthogEventsMock}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: mockConfigs}),
}))

import {trackAuthoringAction} from "../../../src/utils/tabTracking"

describe("trackAuthoringAction", () => {
    beforeEach(() => {
        eventsMock.mockClear()
        posthogEventsMock.mockClear()
        mockConfigs.isAnonymousUsageEnabled = true
    })

    it("sends the action, surface and metadata through both sinks", () => {
        // When
        trackAuthoringAction("task_added", "topology", {task_type: "io.kestra.plugin.core.log.Log", position: "after"})

        // Then
        expect(eventsMock).toHaveBeenCalledTimes(1)
        const [backendPayload] = eventsMock.mock.calls[0]
        expect(backendPayload.type).toBe("PAGE")
        expect(backendPayload.editor_tab).toEqual({
            action: "task_added",
            tab_type: "topology",
            task_type: "io.kestra.plugin.core.log.Log",
            position: "after",
        })

        expect(posthogEventsMock).toHaveBeenCalledTimes(1)
        const [posthogPayload] = posthogEventsMock.mock.calls[0]
        expect(posthogPayload.type).toBe("EDITOR_TAB_ACTION")
        expect(posthogPayload.action).toBe("task_added")
        expect(posthogPayload.tab_type).toBe("topology")
    })

    it("defaults to empty metadata when none is given", () => {
        // When
        trackAuthoringAction("task_deleted", "no_code")

        // Then
        const [backendPayload] = eventsMock.mock.calls[0]
        expect(backendPayload.editor_tab).toEqual({action: "task_deleted", tab_type: "no_code"})
    })

    it("is a no-op when anonymous usage is disabled", () => {
        // Given
        mockConfigs.isAnonymousUsageEnabled = false

        // When
        trackAuthoringAction("task_edited", "topology", {task_type: "io.kestra.plugin.core.log.Log"})

        // Then
        expect(eventsMock).not.toHaveBeenCalled()
        expect(posthogEventsMock).not.toHaveBeenCalled()
    })
})
