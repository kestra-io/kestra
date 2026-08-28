import {describe, it, expect} from "vitest"
import {nextTick} from "vue"
import {createI18n} from "vue-i18n"
import {mount, RouterLinkStub} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"

import Table from "../../../../src/components/dependencies/components/Table.vue"
import Link from "../../../../src/components/dependencies/components/Link.vue"
import en from "../../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: en})

// The flow, execution and namespace views share this table with the asset view, and have
// regressed by inheriting its behaviour; these pin the subtype gate in both directions.
describe("dependencies Table.vue — asset-view gating", () => {
    const row = (subtype: string, id: string) =>
        ({data: {id, type: "NODE", flow: subtype === "ASSET" ? "db.schema.customers" : "my-flow", namespace: "ns", metadata: subtype === "EXECUTION" ? {subtype, id: "exec-1", state: "SUCCESS"} : {subtype}}}) as any

    const mountTable = (subtype: string, elements: any[]) => mount(Table, {
        props: {elements, selected: undefined, subtype: subtype as any},
        global: {plugins: [i18n, KestraDesignSystem], stubs: {RouterLink: RouterLinkStub}},
    })

    // One arrow-count per row, in row order: the base guard gives execution rows none.
    const arrowsPerRow = (wrapper: ReturnType<typeof mountTable>) =>
        wrapper.findAll("section#right").map((right) => right.findAllComponents(RouterLinkStub).length)

    it("keeps the Link name and the guarded arrow outside the asset view", async () => {
        const wrapper = mountTable("EXECUTION", [row("FLOW", "f1"), row("EXECUTION", "e1")])
        // Element Plus registers table columns a couple of ticks after mount.
        await nextTick()
        await nextTick()
        await nextTick()

        expect(wrapper.findAllComponents(Link)).toHaveLength(2)
        expect(wrapper.find("code.name").exists()).toBe(false)
        expect(arrowsPerRow(wrapper)).toEqual([1, 0])
    })

    it("keeps the plain code name and the unguarded arrow in the asset view", async () => {
        const wrapper = mountTable("ASSET", [row("ASSET", "a1"), row("FLOW", "f1")])
        await nextTick()
        await nextTick()
        await nextTick()

        expect(wrapper.findAllComponents(Link)).toHaveLength(0)
        expect(wrapper.findAll("code.name").map((code) => code.text())).toEqual(["db.schema.customers", "my-flow"])
        expect(arrowsPerRow(wrapper)).toEqual([1, 1])
    })
})
