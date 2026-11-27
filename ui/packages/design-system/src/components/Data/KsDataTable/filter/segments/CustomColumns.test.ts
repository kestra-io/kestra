import {describe, test, expect, beforeEach} from "vitest"
import {nextTick} from "vue"
import CustomColumns from "./CustomColumns.vue"
import type {ColumnConfig} from "../composables/useTableColumns"
import {i18nMount} from "../../../../../../tests/units/i18nMount"

const COLUMNS: ColumnConfig[] = [
    {label: "A", prop: "a", default: true},
    {label: "B", prop: "b", default: true},
    {label: "Instance only", prop: "c", default: false, condition: () => false},
]

const mountWith = (visibleColumns: string[], storageKey: string) =>
    i18nMount(CustomColumns, {
        props: {storageKey, columns: COLUMNS, visibleColumns},
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
        const wrapper = i18nMount(CustomColumns, {
            props: {storageKey: "late-columns", columns: [] as ColumnConfig[], visibleColumns: []},
        })
        await nextTick()

        await wrapper.setProps({columns: COLUMNS})
        await nextTick()

        expect(countOf(wrapper as ReturnType<typeof mountWith>)).toBe("2 of 2 columns visible")
    })

    test("shows the search with only a few columns and focuses it on open", async () => {
        const wrapper = i18nMount(CustomColumns, {
            props: {storageKey: "search-focus", columns: COLUMNS, visibleColumns: ["a"]},
            attachTo: document.body,
        })
        await new Promise((resolve) => setTimeout(resolve))

        expect(document.activeElement).toBe(wrapper.find(".search input").element)

        wrapper.unmount()
    })

    test("keeps the resolved columns when the caller re-renders with a new empty visibleColumns array", async () => {
        const wrapper = mountWith([], "rerender-empty")
        await nextTick()

        await wrapper.setProps({visibleColumns: []})

        expect(countOf(wrapper)).toBe("2 of 2 columns visible")
    })

    test("keeps the stored columns when the caller re-renders with a new empty visibleColumns array", async () => {
        localStorage.setItem("columns_rerender-stored", "b")
        const wrapper = mountWith([], "rerender-stored")
        await nextTick()

        await wrapper.setProps({visibleColumns: []})

        expect(countOf(wrapper)).toBe("1 of 2 columns visible")
    })

    test("follows the caller when visibleColumns changes", async () => {
        const wrapper = mountWith(["a", "b"], "caller-driven")
        await nextTick()

        await wrapper.setProps({visibleColumns: ["a"]})

        expect(countOf(wrapper)).toBe("1 of 2 columns visible")
    })

    test("reports what was resolved without persisting it", async () => {
        const wrapper = mountWith([], "no-write-on-open")

        await nextTick()

        expect(wrapper.emitted("updateColumns")).toBeUndefined()
        expect(localStorage.getItem("columns_no-write-on-open")).toBeNull()
    })
})
