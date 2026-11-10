import {beforeEach, describe, expect, it, vi} from "vitest"

const eventsMock = vi.fn()
const posthogEventsMock = vi.fn()
const mockConfigs: {isAnonymousUsageEnabled?: boolean; uuid?: string} = {isAnonymousUsageEnabled: true, uuid: "test-uuid"}
let apiStoreAvailable = true

vi.mock("../stores/api", () => ({
    useApiStore: () => {
        if (!apiStoreAvailable) {
            throw new Error("Pinia is not initialised")
        }
        return {events: eventsMock, posthogEvents: posthogEventsMock}
    },
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: mockConfigs}),
}))

import {
    getTabType,
    makeEvent,
    trackAuthoringAction,
    trackBlueprintSelection,
    trackFileOpen,
    trackPluginDocumentationView,
    type TrackedTab,
} from "./tabTracking"

const tab = (uid: string) => ({uid}) as TrackedTab
const nocodeUid = (payload: Record<string, unknown>) => `nocode-12345${JSON.stringify(payload)}`

describe("tabTracking", () => {
    beforeEach(() => {
        eventsMock.mockReset()
        posthogEventsMock.mockReset()
        mockConfigs.isAnonymousUsageEnabled = true
        apiStoreAvailable = true
    })

    describe("getTabType", () => {
        it("classifies each kind of editor tab correctly", () => {
            // When / Then
            expect(getTabType(tab("code"))).toBe("flow_code")
            expect(getTabType(tab("nocode"))).toBe("flow_no_code")
            expect(getTabType(tab("topology"))).toBe("topology")
            expect(getTabType(tab("doc"))).toBe("documentation")
            expect(getTabType(tab("blueprints"))).toBe("blueprint")
            expect(getTabType(tab("files"))).toBe("files_browser")
        })

        it("classifies a no-code tab by the section of the flow its parentPath points at", () => {
            // When / Then
            expect(getTabType(tab(nocodeUid({parentPath: "tasks[0]"})))).toBe("task_no_code")
            expect(getTabType(tab(nocodeUid({parentPath: "triggers[0]"})))).toBe("trigger_no_code")
            expect(getTabType(tab(nocodeUid({parentPath: "errors[0]"})))).toBe("error_no_code")
            expect(getTabType(tab(nocodeUid({parentPath: "finally[0]"})))).toBe("finally_no_code")
            expect(getTabType(tab(nocodeUid({parentPath: "afterExecution[0]"})))).toBe("afterExecution_no_code")
        })

        it("falls back to task_no_code when a no-code tab has no known parentPath", () => {
            // When / Then
            expect(getTabType(tab(nocodeUid({parentPath: "other"})))).toBe("task_no_code")
            expect(getTabType(tab(nocodeUid({})))).toBe("task_no_code")
        })

        it("returns a sensible value for an unrecognised tab rather than throwing", () => {
            // When / Then
            expect(getTabType(tab("unrecognised-tab"))).toBe("flow_code")
            expect(getTabType(tab("nocode-malformed-json"))).toBe("flow_code")
            expect(getTabType(tab(""))).toBe("flow_code")
        })
    })

    describe("event tracking functions", () => {
        it("trackFileOpen emits one event with the right type and metadata", () => {
            // When
            trackFileOpen("main.py")

            // Then
            expect(eventsMock).toHaveBeenCalledTimes(1)
            const [backendPayload] = eventsMock.mock.calls[0]
            expect(backendPayload.type).toBe("PAGE")
            expect(backendPayload.editor_tab).toEqual({
                action: "files_open",
                tab_type: "files_browser",
                file_name: "main.py",
            })

            expect(posthogEventsMock).toHaveBeenCalledTimes(1)
            const [posthogPayload] = posthogEventsMock.mock.calls[0]
            expect(posthogPayload.type).toBe("EDITOR_TAB_ACTION")
            expect(posthogPayload.action).toBe("files_open")
            expect(posthogPayload.tab_type).toBe("files_browser")
        })

        it("trackBlueprintSelection emits one event with the right type and metadata", () => {
            // When
            trackBlueprintSelection("k8s-deployment")

            // Then
            expect(eventsMock).toHaveBeenCalledTimes(1)
            const [backendPayload] = eventsMock.mock.calls[0]
            expect(backendPayload.type).toBe("PAGE")
            expect(backendPayload.editor_tab).toEqual({
                action: "blueprint_selection",
                tab_type: "blueprint",
                blueprint_name: "k8s-deployment",
            })

            expect(posthogEventsMock).toHaveBeenCalledTimes(1)
            const [posthogPayload] = posthogEventsMock.mock.calls[0]
            expect(posthogPayload.type).toBe("EDITOR_TAB_ACTION")
            expect(posthogPayload.action).toBe("blueprint_selection")
            expect(posthogPayload.tab_type).toBe("blueprint")
        })

        it("trackPluginDocumentationView emits one event with the right type and metadata", () => {
            // When
            trackPluginDocumentationView("io.kestra.plugin.core.log.Log")

            // Then
            expect(eventsMock).toHaveBeenCalledTimes(1)
            const [backendPayload] = eventsMock.mock.calls[0]
            expect(backendPayload.type).toBe("PAGE")
            expect(backendPayload.editor_tab).toEqual({
                action: "plugin_doc",
                tab_type: "documentation",
                documentation_page: "io.kestra.plugin.core.log.Log",
            })

            expect(posthogEventsMock).toHaveBeenCalledTimes(1)
            const [posthogPayload] = posthogEventsMock.mock.calls[0]
            expect(posthogPayload.type).toBe("EDITOR_TAB_ACTION")
            expect(posthogPayload.action).toBe("plugin_doc")
            expect(posthogPayload.tab_type).toBe("documentation")
        })

        it("makeEvent defaults to empty metadata and stamps the instance id and date", () => {
            // When
            makeEvent("open", "flow_code")

            // Then
            const [backendPayload] = eventsMock.mock.calls[0]
            expect(backendPayload.editor_tab).toEqual({action: "open", tab_type: "flow_code"})
            expect(backendPayload.iid).toBe("test-uuid")
            expect(Number.isNaN(Date.parse(backendPayload.date))).toBe(false)
        })
    })

    describe("opt-out", () => {
        it("emits nothing when anonymous usage is disabled", () => {
            // Given
            mockConfigs.isAnonymousUsageEnabled = false

            // When
            trackFileOpen("main.py")
            trackBlueprintSelection("bp-id")
            trackPluginDocumentationView("io.kestra.plugin.core.log.Log")

            // Then
            expect(eventsMock).not.toHaveBeenCalled()
            expect(posthogEventsMock).not.toHaveBeenCalled()
        })
    })

    describe("resilience", () => {
        it("the tracking calls are safe when the analytics store rejects", () => {
            // Given
            eventsMock.mockImplementation(() => {
                throw new Error("Backend analytics error")
            })
            posthogEventsMock.mockImplementation(() => {
                throw new Error("PostHog error")
            })

            // When / Then
            expect(() => trackFileOpen("test.py")).not.toThrow()
            expect(() => trackBlueprintSelection("bp-id")).not.toThrow()
            expect(() => trackPluginDocumentationView("io.kestra.plugin.core.log.Log")).not.toThrow()
            expect(() => trackAuthoringAction("task_added", "topology")).not.toThrow()
        })

        it("the tracking calls are safe when the analytics store is unavailable", () => {
            // Given
            apiStoreAvailable = false

            // When / Then
            expect(() => trackFileOpen("test.py")).not.toThrow()
            expect(() => trackBlueprintSelection("bp-id")).not.toThrow()
            expect(() => trackPluginDocumentationView("io.kestra.plugin.core.log.Log")).not.toThrow()
            expect(() => trackAuthoringAction("task_added", "topology")).not.toThrow()
            expect(eventsMock).not.toHaveBeenCalled()
            expect(posthogEventsMock).not.toHaveBeenCalled()
        })
    })

    describe("trackAuthoringAction", () => {
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
})
