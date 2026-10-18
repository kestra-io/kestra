import {afterEach, describe, test, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../index"
import KsPagination from "./KsPagination.vue"
import {setDesignSystemLocale} from "../../i18n"
import {loadPaginationLocale} from "./paginationLocale"

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

    describe("labels", () => {
        afterEach(() => setDesignSystemLocale("en"))

        const mountWithLabels = () => mount(KsPagination, {
            props: {total: 120, currentPage: 1, pageSize: 50, pageSizes: [50], layout: "total, sizes"},
            global: globalConfig,
        })

        test("keeps Kestra's English wording by default", async () => {
            const wrapper = mountWithLabels()

            await vi.waitFor(() => expect(wrapper.text()).toContain("50 per page"))
            expect(wrapper.text()).toContain("Total 120")
        })

        test("follows the language selected in the app", async () => {
            setDesignSystemLocale("de")
            const wrapper = mountWithLabels()
            await loadPaginationLocale("de")

            await vi.waitFor(() => expect(wrapper.text()).toContain("50 pro Seite"))
            expect(wrapper.text()).toContain("Gesamt 120")
        })

        test("maps Kestra's region codes to Element Plus locales", async () => {
            setDesignSystemLocale("zh_CN")
            const wrapper = mountWithLabels()
            await loadPaginationLocale("zh_CN")

            await vi.waitFor(() => expect(wrapper.text()).toContain("共 120 条"))
        })
    })
})
