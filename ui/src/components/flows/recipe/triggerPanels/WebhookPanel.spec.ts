import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import * as Utils from "../../../../utils/utils"
import WebhookPanel from "./WebhookPanel.vue"

const {toastSuccess} = vi.hoisted(() => ({toastSuccess: vi.fn()}))

vi.mock("../../../../utils/toast", () => ({
    useToast: () => ({success: toastSuccess}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
}))

describe("WebhookPanel", () => {
    afterEach(() => {
        vi.restoreAllMocks()
    })

    it("should copy the webhook URL and confirm it with a toast", async () => {
        const copy = vi.spyOn(Utils, "copy").mockResolvedValue(undefined)
        const wrapper = i18nMount(WebhookPanel, {
            props: {systemNamespace: "system", flowId: "alerts", webhookKey: "secret"},
            messages: {copy: "Copy", copied: "Copied"},
        })

        await wrapper.find("button[aria-label=Copy]").trigger("click")

        expect(copy).toHaveBeenCalledWith(`${window.location.origin}/api/v1/main/executions/webhook/system/alerts/secret`)
        await flushPromises()
        expect(toastSuccess).toHaveBeenCalledWith("Copied")
    })
})
