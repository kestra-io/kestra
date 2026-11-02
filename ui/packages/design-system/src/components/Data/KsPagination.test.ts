import {describe, test, expect, afterEach} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick} from "vue"
import {ElConfigProvider} from "element-plus"
import KestraDesignSystem from "../../index"
import {setDesignSystemLocale} from "../../i18n"
import KsPagination from "./KsPagination.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsPagination", () => {
    test("renders pagination element", () => {
        const wrapper = mount(KsPagination, {
            props: {total: 100, currentPage: 1, pageSize: 10},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-pagination").exists()).toBe(true)
    })

    test("background prop applies is-background class", () => {
        const wrapper = mount(KsPagination, {
            props: {total: 100, currentPage: 1, background: true},
            global: globalConfig,
        })
        expect(wrapper.find(".is-background").exists()).toBe(true)
    })

    test("disabled prop applies is-disabled class", () => {
        const wrapper = mount(KsPagination, {
            props: {total: 100, currentPage: 1, disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".is-disabled").exists()).toBe(true)
    })

    describe("labels follow the app language", () => {
        const layout = "total, sizes, prev, pager, next"

        afterEach(() => setDesignSystemLocale("en"))

        test("keeps the English labels by default", () => {
            const wrapper = mount(KsPagination, {
                props: {total: 100, currentPage: 1, pageSize: 10, layout},
                global: globalConfig,
            })
            expect(wrapper.find(".kel-pagination__total").text()).toBe("Total 100")
            expect(wrapper.findComponent(ElConfigProvider).props("locale").el.pagination.pagesize).toBe(" per page")
        })

        test("translates the labels for a supported language", () => {
            setDesignSystemLocale("de")
            const wrapper = mount(KsPagination, {
                props: {total: 100, currentPage: 1, pageSize: 10, layout},
                global: globalConfig,
            })
            expect(wrapper.find(".kel-pagination__total").text()).toBe("Gesamt 100")
            expect(wrapper.findComponent(ElConfigProvider).props("locale").el.pagination.pagesize).toBe(" pro Seite")
        })

        test("switches language without remounting", async () => {
            const wrapper = mount(KsPagination, {
                props: {total: 100, currentPage: 1, pageSize: 10, layout},
                global: globalConfig,
            })
            expect(wrapper.find(".kel-pagination__total").text()).toBe("Total 100")
            setDesignSystemLocale("de")
            await nextTick()
            expect(wrapper.find(".kel-pagination__total").text()).toBe("Gesamt 100")
        })

        test("maps the app's pt_BR and zh_CN codes to their packs", () => {
            setDesignSystemLocale("zh_CN")
            const wrapper = mount(KsPagination, {
                props: {total: 100, currentPage: 1, pageSize: 10, layout},
                global: globalConfig,
            })
            expect(wrapper.find(".kel-pagination__total").text()).toBe("共 100 条")
        })

        test("falls back to English for a language without a pack", () => {
            setDesignSystemLocale("xx")
            const wrapper = mount(KsPagination, {
                props: {total: 100, currentPage: 1, pageSize: 10, layout},
                global: globalConfig,
            })
            expect(wrapper.find(".kel-pagination__total").text()).toBe("Total 100")
        })
    })
})
