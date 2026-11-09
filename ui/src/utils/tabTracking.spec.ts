import {beforeEach, describe, expect, it, vi} from "vitest"

const eventsMock = vi.fn()
const posthogEventsMock = vi.fn()
const mockConfigs: {isAnonymousUsageEnabled?: boolean; uuid?: string} = {isAnonymousUsageEnabled: true, uuid: "test-uuid"}
const mockEditorPlugin: {cls?: string} = {}
const mockBlueprint: {id?: string} = {}

vi.mock("../stores/api", () => ({
    useApiStore: () => ({events: eventsMock, posthogEvents: posthogEventsMock}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: mockConfigs}),
}))

vi.mock("../stores/plugins", () => ({
    usePluginsStore: () => ({editorPlugin: mockEditorPlugin}),
}))

vi.mock("../stores/blueprints", () => ({
    useBlueprintsStore: () => ({blueprint: mockBlueprint}),
}))

import {
    getTabMetadata,
    getTabType,
    trackAuthoringAction,
    trackBlueprintSelection,
    trackFileOpen,
    trackTabClose,
    trackTabOpen,
    type TrackedTab,
} from "./tabTracking"

const tab = (uid: string) => ({uid}) as TrackedTab
const nocodeUid = (payload: Record<string, unknown>) => `nocode-12345${JSON.stringify(payload)}`

describe("tabTracking", () => {
    beforeEach(() => {
        eventsMock.mockReset()
        posthogEventsMock.mockReset()
        mockConfigs.isAnonymousUsageEnabled = true
        delete mockEditorPlugin.cls
        delete mockBlueprint.id
    })

    describe("getTabType", () => {
        it("classifies each kind of editor tab correctly", () => {
            expect(getTabType(tab("code"))).toBe("flow_code")
            expect(getTabType(tab("nocode"))).toBe("flow_no_code")
            expect(getTabType(tab("topology"))).toBe("topology")
            expect(getTabType(tab("doc"))).toBe("documentation")
            expect(getTabType(tab("blueprints"))).toBe("blueprint")
            expect(getTabType(tab("files"))).toBe("files_browser")

            expect(getTabType(tab(nocodeUid({parentPath: "tasks[0]"})))).toBe("task_no_code")
            expect(getTabType(tab(nocodeUid({parentPath: "triggers[0]"})))).toBe("trigger_no_code")
            expect(getTabType(tab(nocodeUid({parentPath: "errors[0]"})))).toBe("error_no_code")
            expect(getTabType(tab(nocodeUid({parentPath: "finally[0]"})))).toBe("finally_no_code")
            expect(getTabType(tab(nocodeUid({parentPath: "afterExecution[0]"})))).toBe("afterExecution_no_code")
            expect(getTabType(tab(nocodeUid({parentPath: "other"})))).toBe("task_no_code")
        })

        it("returns a sensible value for an unrecognised tab rather than throwing", () => {
            expect(getTabType(tab("unrecognised-tab"))).toBe("flow_code")
            expect(getTabType(tab("nocode-malformed-json"))).toBe("flow_code")
            expect(getTabType(tab(""))).toBe("flow_code")
        })
    })

    describe("getTabMetadata", () => {
        it("builds the expected payload for each tab type", () => {
            mockEditorPlugin.cls = "io.kestra.plugin.core.log.Log"
            expect(getTabMetadata(tab("doc"))).toEqual({
                documentation_page: "io.kestra.plugin.core.log.Log",
            })

            delete mockEditorPlugin.cls
            expect(getTabMetadata(tab("doc"))).toEqual({})

            mockBlueprint.id = "docker-compose-blueprint"
            expect(getTabMetadata(tab("blueprints"))).toEqual({
                blueprint_name: "docker-compose-blueprint",
            })

            delete mockBlueprint.id
            expect(getTabMetadata(tab("blueprints"))).toEqual({})

            const taskTab = tab(nocodeUid({taskType: "io.kestra.plugin.core.log.Log"}))
            expect(getTabMetadata(taskTab)).toEqual({
                task_type: "io.kestra.plugin.core.log.Log",
            })

            expect(getTabMetadata(tab("nocode-12345{invalid-json"))).toEqual({})
            expect(getTabMetadata(tab("code"))).toEqual({})
            expect(getTabMetadata(tab("topology"))).toEqual({})
        })
    })

    describe("event tracking functions", () => {
        it("trackTabOpen emits one event with the right type and metadata", () => {
            mockEditorPlugin.cls = "io.kestra.plugin.core.log.Log"
            trackTabOpen(tab("doc"))

            expect(eventsMock).toHaveBeenCalledTimes(1)
            const [backendPayload] = eventsMock.mock.calls[0]
            expect(backendPayload.type).toBe("PAGE")
            expect(backendPayload.editor_tab).toEqual({
                action: "open",
                tab_type: "documentation",
                documentation_page: "io.kestra.plugin.core.log.Log",
            })

            expect(posthogEventsMock).toHaveBeenCalledTimes(1)
            const [posthogPayload] = posthogEventsMock.mock.calls[0]
            expect(posthogPayload.type).toBe("EDITOR_TAB_ACTION")
            expect(posthogPayload.action).toBe("open")
            expect(posthogPayload.tab_type).toBe("documentation")
        })

        it("trackTabClose emits one event with the right type and metadata", () => {
            trackTabClose(tab("topology"))

            expect(eventsMock).toHaveBeenCalledTimes(1)
            const [backendPayload] = eventsMock.mock.calls[0]
            expect(backendPayload.type).toBe("PAGE")
            expect(backendPayload.editor_tab).toEqual({
                action: "close",
                tab_type: "topology",
            })

            expect(posthogEventsMock).toHaveBeenCalledTimes(1)
            const [posthogPayload] = posthogEventsMock.mock.calls[0]
            expect(posthogPayload.type).toBe("EDITOR_TAB_ACTION")
            expect(posthogPayload.action).toBe("close")
            expect(posthogPayload.tab_type).toBe("topology")
        })

        it("trackFileOpen emits one event with the right type and metadata", () => {
            trackFileOpen("main.py")

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
            trackBlueprintSelection("k8s-deployment")

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
    })

    describe("resilience", () => {
        it("the tracking calls are safe when the analytics store rejects or is unavailable", () => {
            eventsMock.mockImplementation(() => {
                throw new Error("Backend analytics error")
            })
            posthogEventsMock.mockImplementation(() => {
                throw new Error("PostHog error")
            })

            expect(() => trackTabOpen(tab("code"))).not.toThrow()
            expect(() => trackTabClose(tab("code"))).not.toThrow()
            expect(() => trackFileOpen("test.py")).not.toThrow()
            expect(() => trackBlueprintSelection("bp-id")).not.toThrow()
            expect(() => trackAuthoringAction("task_added", "topology")).not.toThrow()
        })
    })

    describe("trackAuthoringAction", () => {
        it("sends the action, surface and metadata through both sinks", () => {
            trackAuthoringAction("task_added", "topology", {task_type: "io.kestra.plugin.core.log.Log", position: "after"})

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
            trackAuthoringAction("task_deleted", "no_code")

            const [backendPayload] = eventsMock.mock.calls[0]
            expect(backendPayload.editor_tab).toEqual({action: "task_deleted", tab_type: "no_code"})
        })

        it("is a no-op when anonymous usage is disabled", () => {
            mockConfigs.isAnonymousUsageEnabled = false

            trackAuthoringAction("task_edited", "topology", {task_type: "io.kestra.plugin.core.log.Log"})

            expect(eventsMock).not.toHaveBeenCalled()
            expect(posthogEventsMock).not.toHaveBeenCalled()
        })
    })
})
