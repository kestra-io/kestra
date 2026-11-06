import {describe, expect, test} from "vitest"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import TableFilters from "./TableFilters.vue"

const namespaces = [
    {label: "Analytics", value: "company.analytics"},
    {label: "Operations", value: "company.operations"},
]

const mountFilters = (props: Partial<InstanceType<typeof TableFilters>["$props"]> = {}) => i18nMount(TableFilters, {
    props: {
        assetView: false,
        namespaces,
        shown: 3,
        total: 12,
        search: "customers",
        namespace: "company.analytics",
        flow: true,
        ...props,
    },
})

describe("TableFilters", () => {
    test.each([
        {assetView: true, key: "dependency.search.placeholders.asset"},
        {assetView: false, key: "dependency.search.placeholders.default"},
    ])("uses $key as the search placeholder when assetView is $assetView", ({assetView, key}) => {
        const wrapper = mountFilters({assetView})

        expect(wrapper.findComponent({name: "KsSearch"}).props("placeholder")).toBe(key)
    })

    test.each([true, false])("renders the flow switch only in asset view (assetView=%s)", (assetView) => {
        const wrapper = mountFilters({assetView})

        expect(wrapper.findComponent({name: "KsSwitch"}).exists()).toBe(assetView)
    })

    test("renders one option per namespace with its label and value", () => {
        const wrapper = mountFilters()
        const options = wrapper.findAllComponents({name: "KsOption"})

        expect(options).toHaveLength(namespaces.length)
        expect(options.map(option => ({label: option.props("label"), value: option.props("value")}))).toEqual(namespaces)
    })

    test("renders no namespace options when the namespace list is empty", () => {
        const wrapper = mountFilters({namespaces: []})

        expect(wrapper.findAllComponents({name: "KsOption"})).toHaveLength(0)
    })

    test.each([
        {shown: 3, total: 12},
        {shown: 0, total: 0},
    ])("renders the result count as $shown / $total", ({shown, total}) => {
        const wrapper = mountFilters({shown, total})

        expect(wrapper.findComponent({name: "KsText"}).text()).toBe(`${shown} / ${total}`)
    })

    test.each([
        {model: "search", component: "KsSearch", initial: "customers", updated: "orders", cleared: ""},
        {model: "namespace", component: "KsSelect", initial: "company.analytics", updated: "company.operations", cleared: undefined},
        {model: "flow", component: "KsSwitch", initial: true, updated: false, cleared: false},
    ])("keeps the $model model bound in both directions", async ({model, component, initial, updated, cleared}) => {
        const wrapper = mountFilters({assetView: true})
        const control = wrapper.findComponent({name: component})

        expect(control.props("modelValue")).toBe(initial)

        await wrapper.setProps({[model]: updated})

        expect(control.props("modelValue")).toBe(updated)

        control.vm.$emit("update:modelValue", initial)
        control.vm.$emit("update:modelValue", cleared)

        expect(wrapper.emitted(`update:${model}`)).toEqual([[initial], [cleared]])
    })
})
