import {afterEach, beforeEach, describe, expect, test} from "vitest"
import {Component, defineComponent, h, nextTick, type PropType} from "vue"
import type {VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../tests/units/i18nMount"
import {useInputAttrs} from "./useInputAttrs"

type HostAttrs = Record<string, unknown>

type HostHarness = {
    wrapper: VueWrapper
    used: ReturnType<typeof useInputAttrs>
    setAttrs: (next: HostAttrs) => Promise<void>
}

let wrappers: VueWrapper[] = []

const mountHost = ({attrs = {}, inputs = 1, noRef = false}: {attrs?: HostAttrs, inputs?: number, noRef?: boolean} = {}): HostHarness => {
    let used!: ReturnType<typeof useInputAttrs>
    const Host = defineComponent({
        setup() {
            used = useInputAttrs("host")
            return () => h("span", {ref: "host"}, Array.from({length: inputs}, () => h("input")))
        },
    })

    // The template ref never renders: the guard on host.value inside apply() is what has to hold.
    const ReflessHost = defineComponent({
        setup() {
            used = useInputAttrs("host")
            return () => h("span")
        },
    })
    if (noRef) {
        const wrapper: VueWrapper = i18nMount(ReflessHost as Component, {props: {}})
        wrappers.push(wrapper)
        return {wrapper, used, setAttrs: async () => {}}
    }
    const hostComponent: Component = Host
    // The host is mounted through a relay, so re-passing hostAttrs re-renders it with new fallthrough
    // attrs instead of remounting: only a re-render fires the onUpdated hook the composable relies on.
    const Relay = defineComponent({
        props: {hostAttrs: {type: Object as PropType<HostAttrs>, required: true}},
        setup(props) {
            return () => h(hostComponent, props.hostAttrs)
        },
    })

    const wrapper: VueWrapper = i18nMount(Relay, {props: {hostAttrs: attrs}})
    wrappers.push(wrapper)

    return {
        wrapper,
        used,
        setAttrs: async (next: HostAttrs) => {
            await wrapper.setProps({hostAttrs: next})
            await nextTick()
        },
    }
}

beforeEach(() => {
    wrappers = []
})

afterEach(() => {
    wrappers.forEach((app) => app.unmount())
    wrappers = []
})

describe("useInputAttrs", () => {
    test("copies data-* and aria-* attrs onto every input inside the host", () => {
        const {wrapper} = mountHost({attrs: {"data-kestra-filter": "range", "aria-invalid": "true"}, inputs: 2})

        for (const input of wrapper.findAll("input")) {
            expect(input.element.getAttribute("data-kestra-filter")).toBe("range")
            expect(input.element.getAttribute("aria-invalid")).toBe("true")
        }
    })

    test("forwards other aria-* keys to the input but not aria-label", () => {
        const {wrapper} = mountHost({attrs: {"aria-label": "added by the caller", "aria-invalid": "true"}})

        const input = wrapper.get("input")
        expect(input.element.getAttribute("aria-invalid")).toBe("true")
        expect(input.element.hasAttribute("aria-label")).toBe(false)
    })

    test("skips data-* and aria-* attrs whose value is undefined, null or false", () => {
        const {wrapper} = mountHost({attrs: {
            "data-empty-attr": undefined,
            "data-null-attr": null,
            "data-false-attr": false,
            "aria-hidden": false,
        }})

        const input = wrapper.get("input")
        expect(input.element.hasAttribute("data-empty-attr")).toBe(false)
        expect(input.element.hasAttribute("data-null-attr")).toBe(false)
        expect(input.element.hasAttribute("data-false-attr")).toBe(false)
        expect(input.element.hasAttribute("aria-hidden")).toBe(false)
    })

    test("pickerAttrs returns exactly the attrs that are not forwarded", () => {
        const {used} = mountHost({attrs: {
            class: "filter-width",
            "aria-label": "added by the caller",
            "data-kestra-filter": "range",
            "aria-invalid": "true",
        }})

        expect(used.pickerAttrs()).toEqual({class: "filter-width", "aria-label": "added by the caller"})
    })

    test("removes an attr from the inputs on the next update once it is no longer passed", async () => {
        const {wrapper, setAttrs} = mountHost({attrs: {"data-stay": "1", "data-gone": "2"}})
        const input = wrapper.get("input").element

        await setAttrs({"data-stay": "1"})

        expect(Object.is(wrapper.get("input").element, input)).toBe(true)
        expect(input.getAttribute("data-stay")).toBe("1")
        expect(input.hasAttribute("data-gone")).toBe(false)
    })

    test("does not throw when the host has no input inside", () => {
        const {used} = mountHost({attrs: {"data-kestra-filter": "range", "aria-live": "polite"}, inputs: 0})

        expect(used.pickerAttrs()).toEqual({})

        const {used: missingRefUsed} = mountHost({attrs: {"data-kestra-filter": "range", "aria-live": "polite"}, noRef: true})

        expect(missingRefUsed.pickerAttrs()).toEqual({})
    })
})
