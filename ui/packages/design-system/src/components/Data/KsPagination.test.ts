import {describe, test, expect, afterEach} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../index"
import {setDesignSystemLocale} from "../../i18n"
import KsPagination from "./KsPagination.vue"

type ElLocale = {name?: string; el: {pagination: {pagesize: string}}}

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsPagination", () => {
    afterEach(() => setDesignSystemLocale("en"))

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
    test("follows the design-system locale (German)", () => {
        setDesignSystemLocale("de")
        const wrapper = mount(KsPagination, {
            props: {total: 100, currentPage: 1, pageSize: 10, layout: "total, sizes"},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("Gesamt")
        expect(wrapper.text()).not.toContain("per page")
    })

    test("keeps the established English labels by default", () => {
        const wrapper = mount(KsPagination, {
            props: {total: 100, currentPage: 1, pageSize: 10, layout: "total, sizes"},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("Total")
        const locale = wrapper.findComponent({name: "ElConfigProvider"}).props("locale") as ElLocale
        expect(locale.el.pagination.pagesize).toBe(" per page")
    })

    test("falls back to English for an unknown locale", () => {
        setDesignSystemLocale("xx")
        const wrapper = mount(KsPagination, {
            props: {total: 100, currentPage: 1, pageSize: 10, layout: "total, sizes"},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("Total")
        const locale = wrapper.findComponent({name: "ElConfigProvider"}).props("locale") as ElLocale
        expect(locale.el.pagination.pagesize).toBe(" per page")
    })
})
