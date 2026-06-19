import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../src/index"
import KsNoData from "../../../src/components/Data/KsNoData.vue"

// vue-i18n is mocked in tests/units/setup.ts so t() echoes the key back;
// assertions therefore check the wired i18n keys, not the translated copy.
const i18n = createI18n({legacy: false, locale: "en"})
const globalConfig = {plugins: [i18n, KestraDesignSystem]}

describe("KsNoData", () => {
    test("renders the default message via i18n keys", () => {
        const wrapper = mount(KsNoData, {global: globalConfig})
        expect(wrapper.text()).toContain("ks_no_data.nothing_here")
        expect(wrapper.text()).toContain("ks_no_data.will_appear")
    })

    test("renders the filter-removed icon", () => {
        const wrapper = mount(KsNoData, {global: globalConfig})
        expect(wrapper.find(".empty-icon").exists()).toBe(true)
    })

    test("renders the title when provided", () => {
        const wrapper = mount(KsNoData, {
            props: {title: "No Flows Found"},
            global: globalConfig,
        })
        expect(wrapper.find("strong").exists()).toBe(true)
        expect(wrapper.text()).toContain("No Flows Found")
    })

    test("shows the default title when neither title nor slot is provided", () => {
        const wrapper = mount(KsNoData, {global: globalConfig})
        expect(wrapper.find("strong").exists()).toBe(true)
        expect(wrapper.text()).toContain("ks_no_data.no_results")
    })

    test("shows the default title alongside slot content when no title is provided", () => {
        const wrapper = mount(KsNoData, {
            slots: {default: "Custom message"},
            global: globalConfig,
        })
        expect(wrapper.find("strong").text()).toContain("ks_no_data.no_results")
        expect(wrapper.text()).toContain("Custom message")
    })

    test("renders the description prop as the body, with the default title above it", () => {
        const wrapper = mount(KsNoData, {
            props: {description: "Adjust your filters and retry"},
            global: globalConfig,
        })
        expect(wrapper.find("strong").text()).toContain("ks_no_data.no_results")
        expect(wrapper.text()).toContain("Adjust your filters and retry")
    })

    test("shows the default body lines beneath a title that has no body of its own", () => {
        const wrapper = mount(KsNoData, {
            props: {title: "No variables available"},
            global: globalConfig,
        })
        expect(wrapper.find("strong").text()).toBe("No variables available")
        expect(wrapper.text()).toContain("ks_no_data.nothing_here")
        expect(wrapper.text()).toContain("ks_no_data.will_appear")
    })

    test("renders title and description together", () => {
        const wrapper = mount(KsNoData, {
            props: {title: "No logs", description: "Try a wider time range"},
            global: globalConfig,
        })
        expect(wrapper.find("strong").text()).toBe("No logs")
        expect(wrapper.text()).toContain("Try a wider time range")
    })
})
