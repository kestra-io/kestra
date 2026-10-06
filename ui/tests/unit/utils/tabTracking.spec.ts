import {describe, it, expect, vi, beforeEach} from "vitest"
import type {Tab} from "../../../src/utils/multiPanelTypes"

const eventsMock = vi.fn()
const posthogEventsMock = vi.fn()
const mockConfigs: {isAnonymousUsageEnabled?: boolean; uuid?: string} = {isAnonymousUsageEnabled: true, uuid: "test-uuid"}
const mockPluginsStore: {editorPlugin?: {cls?: string}} = {}
const mockBlueprintsStore: {blueprint?: {id?: string}} = {}

vi.mock("../../../src/stores/api", () => ({
    useApiStore: () => ({events: eventsMock, posthogEvents: posthogEventsMock}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: mockConfigs}),
}))

vi.mock("../../../src/stores/plugins", () => ({
    usePluginsStore: () => mockPluginsStore,
}))

vi.mock("../../../src/stores/blueprints", () => ({
    useBlueprintsStore: () => mockBlueprintsStore,
}))

import {
    getTabType,
    getTabMetadata,
    trackTabOpen,
    trackTabClose,
    trackFileOpen,
    trackBlueprintSelection,
    trackPluginDocumentationView,
    trackAuthoringAction,
} from "../../../src/utils/tabTracking"

function makeTab(uid: string): Tab {
    return {
        uid,
        button: {icon: null, label: uid},
        component: null,
    }
}

beforeEach(() => {
    eventsMock.mockReset()
    posthogEventsMock.mockReset()
    mockConfigs.isAnonymousUsageEnabled = true
    mockPluginsStore.editorPlugin = undefined
    mockBlueprintsStore.blueprint = undefined
})

describe("getTabType", () => {
    it.each([
        ["code", "flow_code"],
        ["nocode", "flow_no_code"],
        ["topology", "topology"],
        ["doc", "documentation"],
        ["blueprints", "blueprint"],
        ["files", "files_browser"],
    ])("maps standard tab %s to %s", (uid, expected) => {
        expect(getTabType(makeTab(uid))).toBe(expected)
    })

    it.each([
        ["tasks", "task_no_code"],
        ["triggers", "trigger_no_code"],
        ["errors", "error_no_code"],
        ["finally", "finally_no_code"],
        ["afterExecution", "afterExecution_no_code"],
    ])("maps nocode tab with parentPath containing %s to %s", (parentPath, expected) => {
        const uid = `nocode-0000-${JSON.stringify({parentPath: `flow.${parentPath}[0]`})}`
        expect(getTabType(makeTab(uid))).toBe(expected)
    })

    it("defaults to task_no_code for nocode tabs with unrecognised or missing parentPath", () => {
        const uidWithUnknown = `nocode-0000-${JSON.stringify({parentPath: "customHandler"})}`
        expect(getTabType(makeTab(uidWithUnknown))).toBe("task_no_code")

        const uidWithoutPath = `nocode-0000-${JSON.stringify({})}`
        expect(getTabType(makeTab(uidWithoutPath))).toBe("task_no_code")
    })

    it("returns flow_code for malformed nocode tabs without throwing", () => {
        expect(getTabType(makeTab("nocode-invalid-json"))).toBe("flow_code")
    })

    it("returns sensible default flow_code for unrecognised tabs without throwing", () => {
        expect(getTabType(makeTab("unknown"))).toBe("flow_code")
        expect(getTabType(makeTab(""))).toBe("flow_code")
        expect(getTabType(makeTab("settings"))).toBe("flow_code")
    })
})

describe("getTabMetadata", () => {
    it("extracts documentation_page from plugins store for doc tab", () => {
        mockPluginsStore.editorPlugin = {cls: "io.kestra.plugin.core.log.Log"}
        expect(getTabMetadata(makeTab("doc"))).toEqual({
            documentation_page: "io.kestra.plugin.core.log.Log",
        })
    })

    it("returns empty metadata for doc tab when plugin has no class", () => {
        mockPluginsStore.editorPlugin = undefined
        expect(getTabMetadata(makeTab("doc"))).toEqual({})
    })

    it("extracts blueprint_name from blueprints store for blueprints tab", () => {
        mockBlueprintsStore.blueprint = {id: "bp-docker-build"}
        expect(getTabMetadata(makeTab("blueprints"))).toEqual({
            blueprint_name: "bp-docker-build",
        })
    })

    it("returns empty metadata for blueprints tab when blueprint has no id", () => {
        mockBlueprintsStore.blueprint = undefined
        expect(getTabMetadata(makeTab("blueprints"))).toEqual({})
    })

    it("extracts task_type from nocode tab payload", () => {
        const uid = `nocode-0000-${JSON.stringify({taskType: "io.kestra.plugin.core.log.Log"})}`
        expect(getTabMetadata(makeTab(uid))).toEqual({
            task_type: "io.kestra.plugin.core.log.Log",
        })
    })

    it("returns empty metadata for nocode tab when taskType is absent or payload is invalid", () => {
        const uidWithoutTask = `nocode-0000-${JSON.stringify({action: "create"})}`
        expect(getTabMetadata(makeTab(uidWithoutTask))).toEqual({})

        expect(getTabMetadata(makeTab("nocode-bad-json"))).toEqual({})
    })

    it("returns empty metadata for other tabs", () => {
        expect(getTabMetadata(makeTab("code"))).toEqual({})
        expect(getTabMetadata(makeTab("topology"))).toEqual({})
        expect(getTabMetadata(makeTab("files"))).toEqual({})
    })
})

describe("trackTabOpen and trackTabClose", () => {
    it("emits open event with classified tab type and metadata", () => {
        mockPluginsStore.editorPlugin = {cls: "io.kestra.plugin.core.http.Request"}
        trackTabOpen(makeTab("doc"))

        expect(eventsMock).toHaveBeenCalledTimes(1)
        const [backendPayload, backendOptions] = eventsMock.mock.calls[0]
        expect(backendOptions).toEqual({posthog: false})
        expect(backendPayload.type).toBe("PAGE")
        expect(backendPayload.editor_tab).toEqual({
            action: "open",
            tab_type: "documentation",
            documentation_page: "io.kestra.plugin.core.http.Request",
        })

        expect(posthogEventsMock).toHaveBeenCalledTimes(1)
        const [posthogPayload] = posthogEventsMock.mock.calls[0]
        expect(posthogPayload.type).toBe("EDITOR_TAB_ACTION")
        expect(posthogPayload.action).toBe("open")
        expect(posthogPayload.tab_type).toBe("documentation")
    })

    it("emits close event with classified tab type and metadata", () => {
        const uid = `nocode-0000-${JSON.stringify({parentPath: "triggers", taskType: "io.kestra.plugin.core.trigger.Schedule"})}`
        trackTabClose(makeTab(uid))

        expect(eventsMock).toHaveBeenCalledTimes(1)
        const [backendPayload] = eventsMock.mock.calls[0]
        expect(backendPayload.type).toBe("PAGE")
        expect(backendPayload.editor_tab).toEqual({
            action: "close",
            tab_type: "trigger_no_code",
            task_type: "io.kestra.plugin.core.trigger.Schedule",
        })

        expect(posthogEventsMock).toHaveBeenCalledTimes(1)
        const [posthogPayload] = posthogEventsMock.mock.calls[0]
        expect(posthogPayload.action).toBe("close")
        expect(posthogPayload.tab_type).toBe("trigger_no_code")
    })
})

describe("specialized tracking functions", () => {
    it("trackFileOpen emits files_open event with files_browser tab type and file_name", () => {
        trackFileOpen("flow.yaml")

        expect(eventsMock).toHaveBeenCalledTimes(1)
        const [backendPayload] = eventsMock.mock.calls[0]
        expect(backendPayload.editor_tab).toEqual({
            action: "files_open",
            tab_type: "files_browser",
            file_name: "flow.yaml",
        })

        expect(posthogEventsMock).toHaveBeenCalledTimes(1)
        const [posthogPayload] = posthogEventsMock.mock.calls[0]
        expect(posthogPayload.action).toBe("files_open")
        expect(posthogPayload.tab_type).toBe("files_browser")
        expect(posthogPayload.metadata).toEqual({file_name: "flow.yaml"})
    })

    it("trackBlueprintSelection emits blueprint_selection event with blueprint_name", () => {
        trackBlueprintSelection("docker-build")

        expect(eventsMock).toHaveBeenCalledTimes(1)
        const [backendPayload] = eventsMock.mock.calls[0]
        expect(backendPayload.editor_tab).toEqual({
            action: "blueprint_selection",
            tab_type: "blueprint",
            blueprint_name: "docker-build",
        })

        expect(posthogEventsMock).toHaveBeenCalledTimes(1)
        const [posthogPayload] = posthogEventsMock.mock.calls[0]
        expect(posthogPayload.action).toBe("blueprint_selection")
        expect(posthogPayload.tab_type).toBe("blueprint")
        expect(posthogPayload.metadata).toEqual({blueprint_name: "docker-build"})
    })

    it("trackPluginDocumentationView emits plugin_doc event with documentation_page", () => {
        trackPluginDocumentationView("io.kestra.plugin.core.log.Log")

        expect(eventsMock).toHaveBeenCalledTimes(1)
        const [backendPayload] = eventsMock.mock.calls[0]
        expect(backendPayload.editor_tab).toEqual({
            action: "plugin_doc",
            tab_type: "documentation",
            documentation_page: "io.kestra.plugin.core.log.Log",
        })

        expect(posthogEventsMock).toHaveBeenCalledTimes(1)
        const [posthogPayload] = posthogEventsMock.mock.calls[0]
        expect(posthogPayload.action).toBe("plugin_doc")
        expect(posthogPayload.tab_type).toBe("documentation")
        expect(posthogPayload.metadata).toEqual({documentation_page: "io.kestra.plugin.core.log.Log"})
    })
})

describe("error handling and resilience", () => {
    it("does not throw when the api store events call throws", () => {
        eventsMock.mockImplementation(() => {
            throw new Error("Network failure")
        })

        expect(() => trackTabOpen(makeTab("code"))).not.toThrow()
        expect(() => trackFileOpen("test.py")).not.toThrow()
        expect(() => trackBlueprintSelection("test-bp")).not.toThrow()
    })

    it("does not throw when the posthog events call throws", () => {
        posthogEventsMock.mockImplementation(() => {
            throw new Error("PostHog unavailable")
        })

        expect(() => trackTabClose(makeTab("topology"))).not.toThrow()
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
