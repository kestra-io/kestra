import {describe, expect, test} from "vitest"
import {DOMWrapper, flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import TableFilters from "./TableFilters.vue"

const namespaces = [
    {label: "Analytics", value: "company.analytics"},
    {label: "Operations", value: "company.operations"},
]

const mountFilters = (props: Partial<InstanceType<typeof TableFilters>["$props"]> = {}) => i18nMount(TableFilters, {
    attachTo: document.body,
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

const openNamespaces = async (wrapper: ReturnType<typeof mountFilters>) => {
    const select = wrapper.get("[role=combobox]")
    await select.trigger("click")
    await flushPromises()

    return new DOMWrapper(document.body).get(`[id="${select.attributes("aria-controls")}"]`)
}

describe("TableFilters", () => {
    test.each([
        {assetView: true, key: "dependency.search.placeholders.asset"},
        {assetView: false, key: "dependency.search.placeholders.default"},
    ])("uses $key as the search placeholder when assetView is $assetView", ({assetView, key}) => {
        const wrapper = mountFilters({assetView})

        expect(wrapper.get("input[placeholder]:not([role=combobox])").attributes("placeholder")).toBe(key)
    })

    test.each([true, false])("renders the flow switch only in asset view (assetView=%s)", (assetView) => {
        const wrapper = mountFilters({assetView})

        expect(wrapper.find("[role=switch]").exists()).toBe(assetView)
    })

    test("renders one option per namespace and selects its value by label", async () => {
        const wrapper = mountFilters({namespace: undefined})

        for (const [index, namespace] of namespaces.entries()) {
            const listbox = await openNamespaces(wrapper)
            const options = listbox.findAll("[role=option]")

            expect(listbox.isVisible()).toBe(true)
            expect(options.map(option => option.text())).toEqual(namespaces.map(item => item.label))

            await options[index].trigger("click")

            expect(wrapper.emitted("update:namespace")?.at(-1)).toEqual([namespace.value])
        }
    })

    test("renders no namespace options when the namespace list is empty", async () => {
        const wrapper = mountFilters({namespaces: [], namespace: undefined})
        await wrapper.get("[role=combobox]").trigger("click")
        await flushPromises()

        expect(document.querySelectorAll("[role=option]")).toHaveLength(0)
    })

    test.each([
        {shown: 3, total: 12},
        {shown: 0, total: 0},
    ])("renders the result count as $shown / $total", ({shown, total}) => {
        const wrapper = mountFilters({shown, total})

        expect(wrapper.text()).toContain(`${shown} / ${total}`)
    })

    test("keeps the search model bound in both directions", async () => {
        const wrapper = mountFilters()
        const search = wrapper.get<HTMLInputElement>("input[placeholder]:not([role=combobox])")

        expect(search.element.value).toBe("customers")

        await wrapper.setProps({search: "orders"})

        expect(search.element.value).toBe("orders")

        await search.setValue("invoices")
        await search.setValue("")

        expect(wrapper.emitted("update:search")).toEqual([["invoices"], [""]])
    })

    test("keeps the namespace model bound in both directions", async () => {
        const wrapper = mountFilters()
        const listbox = await openNamespaces(wrapper)
        const options = listbox.findAll("[role=option]")

        expect(options[0].attributes("aria-selected")).toBe("true")
        expect(wrapper.text()).toContain("Analytics")

        await wrapper.setProps({namespace: "company.operations"})

        expect(options[1].attributes("aria-selected")).toBe("true")
        expect(wrapper.text()).toContain("Operations")

        await options[0].trigger("click")

        expect(wrapper.emitted("update:namespace")).toEqual([["company.analytics"]])
    })

    test("keeps the flow model bound in both directions", async () => {
        const wrapper = mountFilters({assetView: true})
        const flow = wrapper.get("[role=switch]")

        expect(flow.attributes("aria-checked")).toBe("true")

        await wrapper.setProps({flow: false})

        expect(flow.attributes("aria-checked")).toBe("false")

        await flow.trigger("click")
        await flow.trigger("click")

        expect(wrapper.emitted("update:flow")).toEqual([[true], [false]])
    })
})
