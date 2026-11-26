import {afterEach, describe, expect, it, vi} from "vitest"
import {reactive} from "vue"
import type {VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {useFlowSaveStatus} from "./useFlowSaveStatus"

const flowStore = reactive({
    flow: {id: "f", namespace: "ns", revision: 5, draft: true} as Record<string, unknown> | undefined,
    flowErrors: undefined as string[] | undefined,
    hasPublishedRevision: true as boolean | undefined,
    isCreating: false,
    isReadOnly: false,
    isAllowedEdit: true,
    hasDirtyEditorFiles: false,
})
const autosaveStore = reactive({status: "idle", justPublished: undefined as number | undefined})

vi.mock("../../stores/flow", () => ({useFlowStore: () => flowStore}))
vi.mock("../../stores/flowAutosave", () => ({useFlowAutosaveStore: () => autosaveStore}))
vi.mock("../../composables/useActiveTab", () => ({useActiveTab: () => ({value: "edit"})}))
vi.mock("./useFlowEditorActions", () => ({useFlowEditorActions: () => ({publish: vi.fn()})}))

import FlowSaveStatus from "./FlowSaveStatus.vue"

const messages = {
    flowSaveStatus: {
        upToDate: "Up to date with revision {revision}",
        saving: "Saving draft...",
        draft: "Draft saved · differs from revision {revision}",
        draftUnpublished: "Draft saved · not published yet",
        invalid: "Draft saved · {count} error to fix before publishing | Draft saved · {count} errors to fix before publishing",
        offline: "Saved in this browser only · retrying",
        published: "Published as revision {revision}",
    },
}

let wrapper: VueWrapper | undefined
afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    Object.assign(flowStore, {flow: {id: "f", namespace: "ns", revision: 5, draft: true}, flowErrors: undefined, hasPublishedRevision: true, isReadOnly: false})
    Object.assign(autosaveStore, {status: "idle", justPublished: undefined})
})

function mountStatus() {
    wrapper = i18nMount(FlowSaveStatus, {messages, global: {stubs: {KsDialog: true}}})
    return wrapper.find("[data-test=flow-save-status]")
}

describe("FlowSaveStatus", () => {
    it.each([
        ["a draft over revision 4", {}, {}, "draft", "Draft saved · differs from revision 4"],
        ["a draft that was never published", {hasPublishedRevision: false}, {}, "draft", "Draft saved · not published yet"],
        ["a draft with errors", {flowErrors: ["a", "b"]}, {}, "invalid", "Draft saved · 2 errors to fix before publishing"],
        ["a published flow", {flow: {id: "f", namespace: "ns", revision: 4, draft: false}}, {}, "upToDate", "Up to date with revision 4"],
        ["edits waiting for the debounce", {}, {status: "pending"}, "saving", "Saving draft..."],
        ["edits kept in the browser", {}, {status: "offline"}, "offline", "Saved in this browser only · retrying"],
        ["a publish that just landed", {flow: {id: "f", namespace: "ns", revision: 5, draft: false}}, {justPublished: 5}, "published", "Published as revision 5"],
    ])("shows %s", (_, flow, autosave, state, text) => {
        Object.assign(flowStore, flow)
        Object.assign(autosaveStore, autosave)

        const status = mountStatus()

        expect(status.attributes("data-state")).toBe(state)
        expect(status.text()).toContain(text)
    })

    it("shows nothing while the flow is being created", () => {
        flowStore.isCreating = true

        expect(mountStatus().exists()).toBe(false)
        flowStore.isCreating = false
    })
})

describe("useFlowSaveStatus", () => {
    it("publishes only a valid saved draft, and holds Execute back while changes are unpublished", () => {
        const {canPublish, hasUnpublishedChanges} = useFlowSaveStatus()
        expect(canPublish.value).toBe(true)
        expect(hasUnpublishedChanges.value).toBe(true)

        flowStore.flowErrors = ["broken"]
        expect(canPublish.value).toBe(false)

        flowStore.flowErrors = undefined
        autosaveStore.status = "syncing"
        expect(canPublish.value).toBe(false)
        expect(hasUnpublishedChanges.value).toBe(true)

        autosaveStore.status = "idle"
        flowStore.flow = {id: "f", namespace: "ns", revision: 4, draft: false}
        expect(canPublish.value).toBe(false)
        expect(hasUnpublishedChanges.value).toBe(false)
    })
})
