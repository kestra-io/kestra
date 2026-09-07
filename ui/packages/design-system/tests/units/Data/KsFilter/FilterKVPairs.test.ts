import {defineComponent, h, toRaw} from "vue"
import {describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"

import FilterKVPairs from "../../../../src/components/Data/KsDataTable/filter/layout/FilterKVPairs.vue"
import {Comparators} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const KsInput = defineComponent({
    props: {modelValue: {type: String, default: ""}},
    emits: ["update:modelValue"],
    setup(props, {emit}) {
        return () => h("input", {
            value: props.modelValue,
            onInput: (event: Event) => emit("update:modelValue", (event.target as HTMLInputElement).value),
        })
    },
})

const KsButton = defineComponent({
    props: {disabled: Boolean},
    emits: ["click"],
    setup(props, {emit, slots}) {
        return () => h("button", {
            disabled: props.disabled,
            onClick: () => emit("click"),
        }, slots.default?.())
    },
})

const KsTooltip = defineComponent({
    props: {content: {type: String, default: ""}},
    setup(props, {slots}) {
        return () => h("span", {"data-test": "pair-tooltip", "data-content": props.content}, slots.default?.())
    },
})

const stubs = {
    KsInput,
    KsButton,
    KsTag: {template: "<span><slot /></span>"},
    KsTooltip,
}

const mountPairs = (comparator: Comparators) => mount(FilterKVPairs, {
    props: {modelValue: ["environment:production"], comparator},
    global: {
        mocks: {$t: (key: string) => key},
        components: stubs,
    },
})

const addPair = async (wrapper: ReturnType<typeof mountPairs>, key: string, value: string) => {
    const state = (wrapper.vm as any).$?.setupState
    const rawState = toRaw(state)
    rawState.newKey.value = key
    rawState.newValue.value = value
    state.addPair()
    await wrapper.vm.$nextTick()
    return rawState.detailPairs.value
}

describe("FilterKVPairs", () => {
    test.each([Comparators.IN, Comparators.NOT_IN])(
        "keeps multiple values for the same key with %s",
        async (comparator) => {
            const wrapper = mountPairs(comparator)

            const pairs = await addPair(wrapper, "environment", "staging")

            expect(pairs).toEqual([
                {key: "environment", value: "production"},
                {key: "environment", value: "staging"},
            ])
        },
    )

    test("keeps replacing the value for single-value comparators", async () => {
        const wrapper = mountPairs(Comparators.EQUALS)

        const pairs = await addPair(wrapper, "environment", "staging")

        expect(pairs).toEqual([
            {key: "environment", value: "staging"},
        ])
    })

    test("normalizes same-key values when switching to a single-value comparator", async () => {
        const onUpdate = vi.fn()
        const wrapper = mount(FilterKVPairs, {
            props: {
                modelValue: [
                    "environment:production",
                    "environment:staging",
                    "team:core",
                    "team:platform",
                ],
                comparator: Comparators.IN,
                "onUpdate:modelValue": onUpdate,
            },
            global: {
                mocks: {$t: (key: string) => key},
                components: stubs,
            },
        })

        await wrapper.setProps({comparator: Comparators.EQUALS})
        await wrapper.vm.$nextTick()

        const state = toRaw((wrapper.vm as any).$?.setupState)
        expect(state.detailPairs.value).toEqual([
            {key: "environment", value: "staging"},
            {key: "team", value: "platform"},
        ])
        expect(onUpdate).toHaveBeenLastCalledWith([
            "environment:staging",
            "team:platform",
        ])
    })

    test("exposes the untruncated key and value as a tooltip on every pair", () => {
        const wrapper = mount(FilterKVPairs, {
            props: {
                modelValue: [
                    "foooooooooooooooooooooooooooo1:x",
                    "foooooooooooooooooooooooooooo2:y",
                ],
                comparator: Comparators.IN,
            },
            global: {
                mocks: {$t: (key: string) => key},
                components: stubs,
            },
        })

        const tooltips = wrapper.findAll("[data-test=\"pair-tooltip\"]")

        expect(tooltips.map(tooltip => tooltip.attributes("data-content"))).toEqual([
            "foooooooooooooooooooooooooooo1: x",
            "foooooooooooooooooooooooooooo2: y",
        ])
    })
})
