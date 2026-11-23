import {afterEach, describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h} from "vue"
import * as filteredProps from "../../../utils/filteredProps"
import KsRow from "./KsRow.vue"

const ElRowStub = defineComponent({
    name: "ElRow",
    props: ["gutter", "justify", "align"],
    setup(_, {attrs, slots}) {
        return () => h("div", attrs, slots.default?.())
    },
})

const global = {stubs: {ElRow: ElRowStub}}

afterEach(() => vi.restoreAllMocks())

describe("KsRow", () => {
    test("forwards layout props to ElRow", () => {
        const wrapper = mount(KsRow, {
            props: {gutter: 16, justify: "space-between", align: "middle"},
            global,
        })

        expect(wrapper.findComponent({name: "ElRow"}).props()).toMatchObject({
            gutter: 16,
            justify: "space-between",
            align: "middle",
        })
    })

    test("renders the default slot when provided", () => {
        const wrapper = mount(KsRow, {slots: {default: "Row content"}, global})

        expect(wrapper.text()).toContain("Row content")
    })

    test("does not pass a default slot when absent", () => {
        const wrapper = mount(KsRow, {global})

        expect(wrapper.findComponent({name: "ElRow"}).vm.$slots.default).toBeUndefined()
    })

    test("disables attribute inheritance and forwards attrs explicitly", () => {
        const wrapper = mount(KsRow, {attrs: {id: "row", "data-test": "row"}, global})
        const elRow = wrapper.findComponent({name: "ElRow"})

        expect(KsRow.inheritAttrs).toBe(false)
        expect(elRow.attributes("id")).toBe("row")
        expect(elRow.attributes("data-test")).toBe("row")
    })

    test("lets inherited attrs override filtered props", () => {
        vi.spyOn(filteredProps, "useFilteredProps").mockReturnValue(() => ({id: "filtered"}))
        const wrapper = mount(KsRow, {
            attrs: {id: "inherited"},
            global,
        })

        expect(wrapper.findComponent({name: "ElRow"}).attributes("id")).toBe("inherited")
    })

    test("filters props that are undefined", () => {
        const wrapper = mount(KsRow, {global})
        const elRowProps = wrapper.findComponent({name: "ElRow"}).vm.$.vnode.props

        expect(elRowProps).not.toHaveProperty("gutter")
        expect(elRowProps).not.toHaveProperty("justify")
        expect(elRowProps).not.toHaveProperty("align")
    })
})
