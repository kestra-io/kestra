import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import FlowWarningDialog from "./FlowWarningDialog.vue"

describe("FlowWarningDialog.vue", () => {
    it("renders warning title and markdown without Bootstrap utility classes", () => {
        const wrapper = mount(FlowWarningDialog, {
            global: {
                mocks: {
                    $t: (key: string) => key === "warning" ? "Warning" : "Check warning details",
                },
                stubs: {
                    AlertCircle: {template: "<span class=\"alert-circle-icon\" />"},
                    KsMarkdown: {
                        props: ["content"],
                        template: "<div class=\"ks-markdown-stub\">{{ content }}</div>",
                    },
                },
            },
        })

        const dialog = wrapper.find(".flow-warning-dialog")
        expect(dialog.exists()).toBe(true)
        expect(dialog.classes()).not.toContain("d-flex")
        expect(dialog.classes()).not.toContain("py-3")
        expect(dialog.classes()).not.toContain("align-items-center")

        const icon = wrapper.find(".icon")
        expect(icon.exists()).toBe(true)
        expect(icon.classes()).not.toContain("me-2")

        expect(wrapper.find("strong").text()).toBe("Warning")
        expect(wrapper.find(".ks-markdown-stub").text()).toBe("Check warning details")
    })
})
