import {describe, it, expect, vi, beforeEach} from "vitest"

import {useTabTracking} from "./useTabTracking"

const mocks = vi.hoisted(() => ({
    pluginsStore: {editorPlugin: undefined} as {editorPlugin?: {cls?: string}},
    blueprintsStore: {blueprint: undefined} as {blueprint?: {id?: string}},
    makeEvent: vi.fn(),
    getTabType: vi.fn(),
}))

vi.mock("../stores/plugins", () => ({
    usePluginsStore: () => mocks.pluginsStore,
}))

vi.mock("../stores/blueprints", () => ({
    useBlueprintsStore: () => mocks.blueprintsStore,
}))

vi.mock("../utils/tabTracking", () => ({
    getTabType: mocks.getTabType,
    makeEvent: mocks.makeEvent,
}))

// The source does `value.substring(12)`, so the JSON blob starts at index 12 of the uid:
// "nocode-" (7 chars) + a 5 char segment, then the JSON.
const nocodeUid = (payload: string) => `nocode-12345${payload}`

describe("useTabTracking", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.pluginsStore.editorPlugin = undefined
        mocks.blueprintsStore.blueprint = undefined
        mocks.getTabType.mockReturnValue("some-type")
    })

    describe("metadata", () => {
        it("doc tab carries documentation_page from the plugin store", () => {
            mocks.pluginsStore.editorPlugin = {cls: "io.kestra.plugin.Foo"}

            useTabTracking().trackTabOpen({uid: "doc"} as any)

            expect(mocks.makeEvent).toHaveBeenCalledWith("open", "some-type", {
                documentation_page: "io.kestra.plugin.Foo",
            })
        })

        it("doc tab carries nothing when no plugin is open", () => {
            useTabTracking().trackTabOpen({uid: "doc"} as any)

            expect(mocks.makeEvent).toHaveBeenCalledWith("open", "some-type", {})
        })

        it("blueprints tab carries blueprint_name", () => {
            mocks.blueprintsStore.blueprint = {id: "my-blueprint"}

            useTabTracking().trackTabOpen({uid: "blueprints"} as any)

            expect(mocks.makeEvent).toHaveBeenCalledWith("open", "some-type", {
                blueprint_name: "my-blueprint",
            })
        })

        it("blueprints tab carries nothing when no blueprint is loaded", () => {
            useTabTracking().trackTabOpen({uid: "blueprints"} as any)

            expect(mocks.makeEvent).toHaveBeenCalledWith("open", "some-type", {})
        })

        it("nocode tab carries task_type parsed out of its uid", () => {
            const uid = nocodeUid(JSON.stringify({taskType: "io.kestra.plugin.Bar"}))

            useTabTracking().trackTabOpen({uid} as any)

            expect(mocks.makeEvent).toHaveBeenCalledWith("open", "some-type", {
                task_type: "io.kestra.plugin.Bar",
            })
        })

        it("nocode tab with malformed JSON yields empty metadata without throwing", () => {
            const uid = nocodeUid("{not valid json")
            const {trackTabOpen} = useTabTracking()

            expect(() => trackTabOpen({uid} as any)).not.toThrow()
            expect(mocks.makeEvent).toHaveBeenCalledWith("open", "some-type", {})
        })

        it("unrelated tab yields empty metadata", () => {
            useTabTracking().trackTabOpen({uid: "something-else"} as any)

            expect(mocks.makeEvent).toHaveBeenCalledWith("open", "some-type", {})
        })
    })

    describe("events", () => {
        it("trackTabOpen emits open with the tab type and metadata", () => {
            mocks.getTabType.mockReturnValue("doc-type")
            mocks.pluginsStore.editorPlugin = {cls: "cls-open"}
            const tab = {uid: "doc"} as any

            useTabTracking().trackTabOpen(tab)

            expect(mocks.getTabType).toHaveBeenCalledWith(tab)
            expect(mocks.makeEvent).toHaveBeenCalledTimes(1)
            expect(mocks.makeEvent).toHaveBeenCalledWith("open", "doc-type", {
                documentation_page: "cls-open",
            })
        })

        it("trackTabClose emits close with the tab type and metadata", () => {
            mocks.getTabType.mockReturnValue("blueprints-type")
            mocks.blueprintsStore.blueprint = {id: "bp-close"}
            const tab = {uid: "blueprints"} as any

            useTabTracking().trackTabClose(tab)

            expect(mocks.getTabType).toHaveBeenCalledWith(tab)
            expect(mocks.makeEvent).toHaveBeenCalledTimes(1)
            expect(mocks.makeEvent).toHaveBeenCalledWith("close", "blueprints-type", {
                blueprint_name: "bp-close",
            })
        })
    })
})