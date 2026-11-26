import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {reactive} from "vue"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"
import type {Flow} from "../../stores/flow"

const requestError = vi.fn()
const publishDraft = vi.fn(() => Promise.resolve("saved"))
const flowStore = reactive<{flow?: Flow, hasPublishedRevision?: boolean, publishDraft: typeof publishDraft}>({publishDraft})
const permission = {canUpdate: true}

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => flowStore,
}))

vi.mock("../../utils/toast", () => ({
    useToast: () => ({requestError}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => permission.canUpdate}}),
}))

import FlowDraftBanner from "./FlowDraftBanner.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

let wrapper: VueWrapper | undefined

afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
})

function mountBanner() {
    wrapper = i18nMount(FlowDraftBanner, {global: {plugins: [KestraDesignSystem]}})
    return wrapper
}

const draft: Flow = {id: "f", namespace: "ns", source: "", draft: true, revision: 2}

describe("FlowDraftBanner", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        permission.canUpdate = true
        flowStore.flow = draft
        flowStore.hasPublishedRevision = false
    })

    it("says that triggers and other flows do not start a flow with no published revision", () => {
        expect(mountBanner().text()).toContain("flow_draft_banner.draft_only.description")
    })

    it("says that triggers and other flows still run the published revision under the draft", () => {
        flowStore.hasPublishedRevision = true

        expect(mountBanner().text()).toContain("flow_draft_banner.over_published.description")
    })

    it.each([
        {state: "a published flow", flow: {...draft, draft: false}, hasPublishedRevision: true},
        {state: "a deleted draft", flow: {...draft, deleted: true}, hasPublishedRevision: false},
        {state: "a draft whose revisions are not known yet", flow: draft, hasPublishedRevision: undefined},
    ])("shows nothing on $state", ({flow, hasPublishedRevision}) => {
        flowStore.flow = flow
        flowStore.hasPublishedRevision = hasPublishedRevision

        expect(mountBanner().find("[data-test='flow-draft-banner']").exists()).toBe(false)
    })

    it("publishes the saved draft in one click, without asking for confirmation", async () => {
        await mountBanner().get("[data-test='flow-draft-banner-publish']").trigger("click")

        expect(publishDraft).toHaveBeenCalledExactlyOnceWith(draft)
    })

    it("offers no Publish to a user who cannot edit the flow", () => {
        permission.canUpdate = false

        const banner = mountBanner()

        expect(banner.find("[data-test='flow-draft-banner']").exists()).toBe(true)
        expect(banner.find("[data-test='flow-draft-banner-publish']").exists()).toBe(false)
    })

    it("reports a failed publish instead of leaving an unhandled rejection", async () => {
        const failure = new Error("boom")
        publishDraft.mockRejectedValueOnce(failure)
        const banner = mountBanner()

        await banner.get("[data-test='flow-draft-banner-publish']").trigger("click")
        await flushPromises()

        expect(requestError).toHaveBeenCalledExactlyOnceWith(failure, "flow_publish.failed")
        expect(banner.get("[data-test='flow-draft-banner-publish']").classes()).not.toContain("is-loading")
    })
})
