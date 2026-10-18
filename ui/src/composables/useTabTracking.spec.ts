import {beforeEach, describe, expect, it, vi} from "vitest"
import type {TrackedTab} from "../utils/tabTracking"

const pluginsStoreState = {
    editorPlugin: undefined as {cls?: string} | undefined,
}

const blueprintsStoreState = {
    blueprint: undefined as {id?: string} | undefined,
}

vi.mock("../stores/plugins", () => ({
    usePluginsStore: () => pluginsStoreState,
}))

vi.mock("../stores/blueprints", () => ({
    useBlueprintsStore: () => blueprintsStoreState,
}))

const mockMakeEvent = vi.fn()

vi.mock("../utils/tabTracking", async (importOriginal) => {
    const actual = await importOriginal<typeof import("../utils/tabTracking")>()
    return {
        ...actual,
        makeEvent: (...args: unknown[]) => mockMakeEvent(...args),
    }
})

import {useTabTracking} from "./useTabTracking"

function createTab(uid: string): TrackedTab {
    return {
        uid,
        name: "Tab",
        active: true,
        closeable: true,
    }
}

describe("useTabTracking", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        pluginsStoreState.editorPlugin = undefined
        blueprintsStoreState.blueprint = undefined
    })

    it("carries documentation_page on a doc tab from plugin store, and nothing when no plugin is open", () => {
        const {trackTabOpen} = useTabTracking()

        // With open editor plugin
        pluginsStoreState.editorPlugin = {cls: "io.kestra.plugin.core.log.Log"}
        trackTabOpen(createTab("doc"))
        expect(mockMakeEvent).toHaveBeenCalledWith(
            "open",
            "documentation",
            {documentation_page: "io.kestra.plugin.core.log.Log"},
        )

        mockMakeEvent.mockClear()

        // Without open editor plugin
        pluginsStoreState.editorPlugin = undefined
        trackTabOpen(createTab("doc"))
        expect(mockMakeEvent).toHaveBeenCalledWith(
            "open",
            "documentation",
            {},
        )
    })

    it("carries blueprint_name on a blueprints tab, and nothing when no blueprint is loaded", () => {
        const {trackTabOpen} = useTabTracking()

        // With loaded blueprint
        blueprintsStoreState.blueprint = {id: "my-blueprint-id"}
        trackTabOpen(createTab("blueprints"))
        expect(mockMakeEvent).toHaveBeenCalledWith(
            "open",
            "blueprint",
            {blueprint_name: "my-blueprint-id"},
        )

        mockMakeEvent.mockClear()

        // Without loaded blueprint
        blueprintsStoreState.blueprint = undefined
        trackTabOpen(createTab("blueprints"))
        expect(mockMakeEvent).toHaveBeenCalledWith(
            "open",
            "blueprint",
            {},
        )
    })

    it("carries task_type parsed out of a nocode- tab uid", () => {
        const {trackTabOpen} = useTabTracking()

        const payload = JSON.stringify({taskType: "io.kestra.plugin.scripts.shell.Commands"})
        const uid = `nocode-1234-${payload}`

        trackTabOpen(createTab(uid))
        expect(mockMakeEvent).toHaveBeenCalledWith(
            "open",
            "task_no_code",
            {task_type: "io.kestra.plugin.scripts.shell.Commands"},
        )
    })

    it("yields empty metadata rather than throwing for a nocode- tab with malformed JSON", () => {
        const {trackTabOpen} = useTabTracking()

        const uid = "nocode-1234-invalid-json-content{"

        expect(() => trackTabOpen(createTab(uid))).not.toThrow()
        expect(mockMakeEvent).toHaveBeenCalledWith(
            "open",
            "flow_code",
            {},
        )
    })

    it("yields empty metadata for an unrelated tab", () => {
        const {trackTabOpen} = useTabTracking()

        trackTabOpen(createTab("files"))
        expect(mockMakeEvent).toHaveBeenCalledWith(
            "open",
            "files_browser",
            {},
        )

        mockMakeEvent.mockClear()

        trackTabOpen(createTab("topology"))
        expect(mockMakeEvent).toHaveBeenCalledWith(
            "open",
            "topology",
            {},
        )
    })

    it("emits open and close actions with tab type and metadata from trackTabOpen and trackTabClose", () => {
        const {trackTabOpen, trackTabClose} = useTabTracking()

        pluginsStoreState.editorPlugin = {cls: "io.kestra.plugin.core.http.Request"}
        const docTab = createTab("doc")

        trackTabOpen(docTab)
        expect(mockMakeEvent).toHaveBeenCalledWith(
            "open",
            "documentation",
            {documentation_page: "io.kestra.plugin.core.http.Request"},
        )

        mockMakeEvent.mockClear()

        trackTabClose(docTab)
        expect(mockMakeEvent).toHaveBeenCalledWith(
            "close",
            "documentation",
            {documentation_page: "io.kestra.plugin.core.http.Request"},
        )
    })
})
