import {describe, expect, test} from "vitest"
import {i18nMount} from "../../../tests/unit/i18nMount"
import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
import FlowWarningDialog from "./FlowWarningDialog.vue"

describe("FlowWarningDialog", () => {
    test("renders the translated warning heading and alert icon", () => {
        const wrapper = i18nMount(FlowWarningDialog, {
            messages: {
                warning: "Warning",
                trigger_check_warning: "Trigger checks are disabled.",
            },
        })

        expect(wrapper.find("strong").text()).toBe("Warning")
        expect(wrapper.findComponent(AlertCircle).exists()).toBe(true)
    })

    test("passes the translated trigger warning to KsMarkdown", () => {
        const wrapper = i18nMount(FlowWarningDialog, {
            messages: {
                warning: "Warning",
                trigger_check_warning: "Trigger checks are disabled.",
            },
            global: {
                stubs: {
                    KsMarkdown: {
                        props: ["content"],
                        template: "<div data-test=\"warning-markdown\">{{ content }}</div>",
                    },
                },
            },
        })

        expect(wrapper.get("[data-test=warning-markdown]").text()).toBe("Trigger checks are disabled.")
        expect(wrapper.text()).not.toContain("warning")
        expect(wrapper.text()).not.toContain("trigger_check_warning")
    })
})
