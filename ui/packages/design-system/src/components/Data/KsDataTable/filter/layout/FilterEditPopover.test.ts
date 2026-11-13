import {afterEach, describe, expect, test} from "vitest"
import {nextTick} from "vue"
import type {VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../../../../../tests/units/i18nMount"
import FilterEditPopover from "./FilterEditPopover.vue"
import FilterEditPopper from "./FilterEditPopper.vue"
import {Comparators, type AppliedFilter, type FilterKeyConfig} from "../utils/filterTypes"

const stateKey: FilterKeyConfig = {
    key: "state",
    label: "State",
    valueType: "multi-select",
    comparators: [Comparators.IN],
}

const filterWith = (value: AppliedFilter["value"]): AppliedFilter => ({
    id: "f1",
    key: "state",
    keyLabel: "State",
    comparator: Comparators.IN,
    comparatorLabel: "In",
    value,
    valueLabel: "",
})

type PopoverWrapper = VueWrapper<InstanceType<typeof FilterEditPopover>>

let wrapper: PopoverWrapper | undefined

const mountPopover = (filter: AppliedFilter, filterKey: FilterKeyConfig = stateKey) => {
    wrapper = i18nMount(FilterEditPopover, {
        props: {filter, filterKey},
        global: {stubs: {FilterEditPopper: true}},
    }) as PopoverWrapper
    return wrapper
}

const popper = (popover: PopoverWrapper) => popover.findComponent(FilterEditPopper)

const toggle = async (popover: PopoverWrapper) => {
    popover.vm.toggleDialog()
    await nextTick()
}

describe("FilterEditPopover", () => {
    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
    })

    test("teleports nothing while it is closed", () => {
        const popover = mountPopover(filterWith(["RUNNING"]))

        expect(popper(popover).exists()).toBe(false)
        expect(document.body.querySelector(".edit-overlay")).toBeNull()
    })

    test("toggleDialog opens it and closes it again", async () => {
        const popover = mountPopover(filterWith(["RUNNING"]))

        await toggle(popover)
        expect(popover.vm.isDialogVisible).toBe(true)
        expect(popper(popover).exists()).toBe(true)

        await toggle(popover)
        expect(popover.vm.isDialogVisible).toBe(false)
        expect(popper(popover).exists()).toBe(false)
    })

    test.each([
        {label: "null", value: null},
        {label: "an empty string", value: ""},
        {label: "an empty array", value: []},
    ])("emits remove with the filter id when closed with $label", async ({value}) => {
        const popover = mountPopover(filterWith(value as AppliedFilter["value"]))

        await toggle(popover)
        popper(popover).vm.$emit("close")
        await nextTick()

        expect(popover.emitted("remove")).toEqual([["f1"]])
        expect(popover.vm.isDialogVisible).toBe(false)
    })

    test("keeps an empty filter whose key is visible by default", async () => {
        const popover = mountPopover(filterWith([]), {...stateKey, visibleByDefault: true})

        await toggle(popover)
        await toggle(popover)

        expect(popover.emitted("remove")).toBeUndefined()
        expect(popover.vm.isDialogVisible).toBe(false)
    })

    test("keeps a filter that has a value", async () => {
        const popover = mountPopover(filterWith(["RUNNING"]))

        await toggle(popover)
        await toggle(popover)

        expect(popover.emitted("remove")).toBeUndefined()
    })

    test("re-emits an update from the popper and stays open", async () => {
        const popover = mountPopover(filterWith([]))
        const updated = filterWith(["FAILED"])

        await toggle(popover)
        popper(popover).vm.$emit("update", updated)
        await nextTick()

        expect(popover.emitted("update")).toEqual([[updated]])
        expect(popover.vm.isDialogVisible).toBe(true)
        expect(popper(popover).exists()).toBe(true)
    })

    test("re-emits a remove from the popper and closes", async () => {
        const popover = mountPopover(filterWith(["RUNNING"]))

        await toggle(popover)
        popper(popover).vm.$emit("remove", "f1")
        await nextTick()

        expect(popover.emitted("remove")).toEqual([["f1"]])
        expect(popover.vm.isDialogVisible).toBe(false)
        expect(popper(popover).exists()).toBe(false)
    })
})