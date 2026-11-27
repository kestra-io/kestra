import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"
import * as Utils from "../../utils/utils"
import LogValueActions from "./LogValueActions.vue"

const {toastSuccess} = vi.hoisted(() => ({toastSuccess: vi.fn()}))

vi.mock("../../utils/toast", () => ({
    useToast: () => ({success: toastSuccess}),
}))

describe("LogValueActions", () => {
    afterEach(() => {
        vi.restoreAllMocks()
    })

    it("should copy the value and confirm it with a toast", async () => {
        const copy = vi.spyOn(Utils, "copy").mockResolvedValue(undefined)
        const wrapper = i18nMount(LogValueActions, {
            props: {field: "thread", value: "worker-thread-4"},
            slots: {default: "worker-thread-4"},
            messages: {copied: "Copied", copy_value: "Copy value"},
            global: {
                stubs: {
                    KsPopover: {
                        template: "<div><slot name=\"reference\" /><slot /></div>",
                    },
                },
            },
        })

        await wrapper.findAll("button").find((button) => button.text() === "Copy value")!.trigger("click")

        expect(copy).toHaveBeenCalledWith("worker-thread-4")
        await flushPromises()
        expect(toastSuccess).toHaveBeenCalledWith("Copied")
    })
})
