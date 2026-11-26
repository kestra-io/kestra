import {describe, it, expect} from "vitest"
import {nextTick} from "vue"
import {RouterLinkStub} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

import KestraDesignSystem from "@kestra-io/design-system"
import Table from "./Table.vue"
import Link from "./Link.vue"
import TableFilters from "./TableFilters.vue"
import en from "../../../translations/en.json"
import {NODE, FLOW, EXECUTION, ASSET} from "../utils/types"
import type {Element, Types} from "../utils/types"

// The flow, execution and namespace views share this table with the asset view, and have
// regressed by inheriting its behaviour; these pin the subtype gate in both directions.
describe("dependencies Table.vue — asset-view gating", () => {
    const row = (subtype: Types, id: string): Element => ({
        data: {
            id,
            type: NODE,
            flow: subtype === ASSET ? "db.schema.customers" : "my-flow",
            namespace: "ns",
            metadata: subtype === EXECUTION
                ? {subtype: EXECUTION, id: "exec-1", state: "SUCCESS"}
                : subtype === ASSET
                    ? {subtype: ASSET}
                    : {subtype: FLOW},
        },
    })

    const mountTable = (subtype: Types, elements: Element[]) => i18nMount(Table, {
        locales: en,
        props: {elements, selected: undefined, subtype},
        global: {plugins: [KestraDesignSystem], stubs: {RouterLink: RouterLinkStub}},
    })

    // One arrow-count per row, in row order: the base guard gives execution rows none.
    const arrowsPerRow = (wrapper: ReturnType<typeof mountTable>) =>
        wrapper.findAll("section#right").map((right) => right.findAllComponents(RouterLinkStub).length)

    it("keeps the Link name and the guarded arrow outside the asset view", async () => {
        const wrapper = mountTable(EXECUTION, [row(FLOW, "f1"), row(EXECUTION, "e1")])
        // Element Plus registers table columns a couple of ticks after mount.
        await nextTick()
        await nextTick()
        await nextTick()

        expect(wrapper.findAllComponents(Link)).toHaveLength(2)
        expect(wrapper.find("code.name").exists()).toBe(false)
        expect(arrowsPerRow(wrapper)).toEqual([1, 0])
    })

    it("keeps the plain code name and the unguarded arrow in the asset view", async () => {
        const wrapper = mountTable(ASSET, [row(ASSET, "a1"), row(FLOW, "f1")])
        await nextTick()
        await nextTick()
        await nextTick()

        expect(wrapper.findAllComponents(Link)).toHaveLength(0)
        expect(wrapper.findAll("code.name").map((code) => code.text())).toEqual(["db.schema.customers", "my-flow"])
        expect(arrowsPerRow(wrapper)).toEqual([1, 1])
    })
})

describe("dependencies Table.vue — tool filter", () => {
    const asset = (id: string, tools?: string[]): Element => ({
        data: {id, type: NODE, flow: id, metadata: {subtype: ASSET, assetMetadata: tools ? {"system.tools": tools} : {}}},
    })

    it("narrows the graph to the assets a tool wrote, and offers each tool once", async () => {
        const shown: string[][] = []
        const wrapper = i18nMount(Table, {
            locales: en,
            props: {
                elements: [asset("vm", ["GCP", "Terraform", "Ansible"]), asset("orders", ["PostgreSQL", "dbt"]), asset("customers", ["PostgreSQL", "dbt"]), asset("raw"), {
                    data: {id: "flow", type: NODE, flow: "deploy", namespace: "company.infra", metadata: {subtype: FLOW}},
                }],
                selected: undefined,
                subtype: ASSET,
                highlightShown: (ids: string[]) => shown.push(ids),
            },
            global: {plugins: [KestraDesignSystem], stubs: {RouterLink: RouterLinkStub, AssetTools: true}},
        })
        const filters = wrapper.findComponent(TableFilters)

        expect(filters.props("tools")).toEqual(["Ansible", "GCP", "PostgreSQL", "Terraform", "dbt"])

        filters.vm.$emit("update:tool", "Terraform")
        await nextTick()

        expect(shown.at(-1)).toEqual(["vm"])
    })

    it("drops the tool filter once no asset of that tool is left in the graph", async () => {
        const shown: string[][] = []
        const wrapper = i18nMount(Table, {
            locales: en,
            props: {elements: [asset("vm", ["Terraform"]), asset("raw")], selected: undefined, subtype: ASSET, highlightShown: (ids: string[]) => shown.push(ids)},
            global: {plugins: [KestraDesignSystem], stubs: {RouterLink: RouterLinkStub, AssetTools: true}},
        })
        wrapper.findComponent(TableFilters).vm.$emit("update:tool", "Terraform")
        await nextTick()

        await wrapper.setProps({elements: [asset("raw")]})
        await nextTick()

        expect(shown.at(-1)).toEqual(["raw"])
    })
})
