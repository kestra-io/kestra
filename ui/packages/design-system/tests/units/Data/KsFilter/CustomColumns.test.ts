import {describe, test, expect, beforeEach} from "vitest"
import {nextTick} from "vue"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../../src/index"
import CustomColumns from "../../../../src/components/Data/KsDataTable/filter/segments/CustomColumns.vue"
import type {ColumnConfig} from "../../../../src/components/Data/KsDataTable/filter/composables/useTableColumns"

const COLUMNS: ColumnConfig[] = [
    {label: "A", prop: "a", default: true},
    {label: "B", prop: "b", default: true},
    {label: "Instance only", prop: "c", default: false, condition: () => false},
]

const mountWith = (visibleColumns: string[], storageKey: string) =>
    mount(CustomColumns, {
        props: {storageKey, columns: COLUMNS, visibleColumns},
        global: {plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem]},
    })

const countOf = (wrapper: ReturnType<typeof mountWith>) =>
    wrapper.get("[data-test=visible-columns-count]").text()

describe("CustomColumns", () => {
    beforeEach(() => localStorage.clear())

    test("excludes columns hidden by their condition from the count", () => {
        const wrapper = mountWith(["a", "b"], "condition-hidden")

        expect(countOf(wrapper)).toBe("2 of 2 columns visible")
    })

    test("does not count a stored column that its condition hides", () => {
        const wrapper = mountWith(["a", "b", "c"], "condition-hidden-stored")

        expect(countOf(wrapper)).toBe("2 of 2 columns visible")
    })

    test("counts the resolved columns when the caller passes no visibleColumns", async () => {
        const wrapper = mountWith([], "no-visible-columns")

        await nextTick()

        expect(countOf(wrapper)).toBe("2 of 2 columns visible")
    })

    // The panel is built before its page's columns reach it, so `columns` is `[]` on the first
    // render. Resolution used to run against that empty snapshot and never re-ran, leaving the
    // counter on "0 of N" until a toggle recomputed it.
    test("counts the columns once they arrive after the first render", async () => {
        const wrapper = mount(CustomColumns, {
            props: {storageKey: "late-columns", columns: [] as ColumnConfig[], visibleColumns: []},
            global: {plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem]},
        })
        await nextTick()

        await wrapper.setProps({columns: COLUMNS})
        await nextTick()

        expect(countOf(wrapper as ReturnType<typeof mountWith>)).toBe("2 of 2 columns visible")
    })

    test("reports what was resolved without persisting it", async () => {
        const wrapper = mountWith([], "no-write-on-open")

        await nextTick()

        expect(wrapper.emitted("updateColumns")).toBeUndefined()
        expect(localStorage.getItem("columns_no-write-on-open")).toBeNull()
    })
})
