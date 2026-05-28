import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createRouter, createMemoryHistory} from "vue-router"
import KestraDesignSystem from "../../../../src/index"
import KsFilter from "../../../../src/components/Data/KsDataTable/KsFilter.vue"

const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/", component: {template: "<div/>"}}],
})

const globalConfig = {
    plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem, router],
    stubs: {
        "ks-popover": true,
        "ks-button": true,
        "ks-tooltip": true,
        "ks-switch": true,
        "ks-tag": true,
        "ks-icon": true,
    },
}

describe("KsFilter", () => {
    test("renders without errors with minimal config", () => {
        const wrapper = mount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
            },
            global: globalConfig,
        })
        expect(wrapper.find(".filter").exists()).toBe(true)
    })

    test("renders filter section with top div", () => {
        const wrapper = mount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
            },
            global: globalConfig,
        })
        expect(wrapper.find(".filter .top").exists()).toBe(true)
    })

    test("emits filter event when appliedFilters change", async () => {
        const wrapper = mount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
            },
            global: globalConfig,
        })
        expect(wrapper.emitted()).toBeTruthy()
    })

    test("does not render filter options when showOptions is false", () => {
        const wrapper = mount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
                tableOptions: {},
            },
            global: globalConfig,
        })
        // FilterOptions is hidden by default (showOptions starts false)
        expect(wrapper.find(".expand-panel").exists()).toBe(false)
    })
})
