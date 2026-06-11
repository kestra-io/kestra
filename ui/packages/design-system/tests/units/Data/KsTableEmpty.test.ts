import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../src/index"
import KsTableEmpty from "../../../src/components/Data/KsTableEmpty.vue"

// vue-i18n is mocked in tests/units/setup.ts so t() echoes the key back;
// assertions therefore check the wired i18n keys, not the translated copy.
const i18n = createI18n({legacy: false, locale: "en"})
const globalConfig = {plugins: [i18n, KestraDesignSystem]}

describe("KsTableEmpty", () => {
    test("renders the default message via i18n keys", () => {
        const wrapper = mount(KsTableEmpty, {global: globalConfig})
        expect(wrapper.text()).toContain("ks_table_empty.nothing_here")
        expect(wrapper.text()).toContain("ks_table_empty.adjust_filters")
    })

    test("renders the filter-removed icon", () => {
        const wrapper = mount(KsTableEmpty, {global: globalConfig})
        expect(wrapper.find(".empty-icon").exists()).toBe(true)
    })

    test("renders the title when provided", () => {
        const wrapper = mount(KsTableEmpty, {
            props: {title: "No Flows Found"},
            global: globalConfig,
        })
        expect(wrapper.find("strong").exists()).toBe(true)
        expect(wrapper.text()).toContain("No Flows Found")
    })

    test("omits the title element when title is absent", () => {
        const wrapper = mount(KsTableEmpty, {global: globalConfig})
        expect(wrapper.find("strong").exists()).toBe(false)
    })
})
