import {describe, expect, it} from "vitest"
import {defineComponent} from "vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import ValidationError from "./ValidationError.vue"

describe("ValidationError.vue", () => {
    const mountComponent = (props = {}) => {
        return i18nMount(ValidationError, {
            props,
            global: {
                stubs: {
                    KsCodeStatus: defineComponent({
                        name: "KsCodeStatus",
                        props: ["status", "label", "iconOnly"],
                        template: "<div class=\"ks-code-status\" :data-status=\"status\">{{ label }}</div>",
                    }),
                    KsTooltip: defineComponent({
                        name: "KsTooltip",
                        props: ["popperClass"],
                        template: "<div class=\"ks-tooltip\" :data-popper-class=\"popperClass\"><slot /><slot name=\"content\" /></div>",
                    }),
                    KsContainer: {template: "<div class=\"ks-container\"><slot /></div>"},
                    KsHeader: {template: "<header class=\"ks-header\"><slot /></header>"},
                    KsMain: {template: "<main class=\"ks-main\"><slot /></main>"},
                    AlertCircle: {template: "<span class=\"alert-circle-stub\" />"},
                    Alert: {template: "<span class=\"alert-stub\" />"},
                },
            },
        })
    }

    it("renders valid status when no errors, warnings, or infos provided", () => {
        const wrapper = mountComponent()
        const status = wrapper.findComponent({name: "KsCodeStatus"})
        expect(status.exists()).toBe(true)
        expect(status.attributes("data-status")).toBe("valid")
    })

    it("renders error tooltip with validation-tooltip-popper class", () => {
        const wrapper = mountComponent({errors: ["Syntax error at line 5"]})
        const tooltip = wrapper.findComponent({name: "KsTooltip"})
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.attributes("data-popper-class")).toBe("validation-tooltip-popper")

        const status = wrapper.findComponent({name: "KsCodeStatus"})
        expect(status.attributes("data-status")).toBe("error")
        expect(wrapper.html()).not.toContain("p-0 bg-transparent")
        expect(wrapper.html()).not.toContain("align-middle")
    })

    it("renders warning tooltip with validation-tooltip-popper class", () => {
        const wrapper = mountComponent({warnings: ["Deprecated task usage"]})
        const tooltip = wrapper.findComponent({name: "KsTooltip"})
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.attributes("data-popper-class")).toBe("validation-tooltip-popper")

        const status = wrapper.findComponent({name: "KsCodeStatus"})
        expect(status.attributes("data-status")).toBe("warning")
    })

    it("renders info tooltip when only infos provided", () => {
        const wrapper = mountComponent({infos: ["Plugin documentation updated"]})
        const tooltip = wrapper.findComponent({name: "KsTooltip"})
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.attributes("data-popper-class")).toBe("validation-tooltip-popper")

        const status = wrapper.findComponent({name: "KsCodeStatus"})
        expect(status.attributes("data-status")).toBe("info")
    })
})
