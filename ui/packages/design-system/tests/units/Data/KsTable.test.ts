import {describe, test, expect} from "vitest"
import KsTable from "../../../src/components/Data/KsTable/KsTable.vue"
import KsTableColumn from "../../../src/components/Data/KsTable/KsTableColumn.vue"
import {i18nMount} from "../i18nMount"

const globalConfig = {
}

describe("KsTable", () => {
    test("renders table element", () => {
        const wrapper = i18nMount(KsTable, {
            props: {data: [{name: "test"}]},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-table").exists()).toBe(true)
    })

    test("exposes clearSelection method", () => {
        const wrapper = i18nMount(KsTable, {
            props: {data: []},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).clearSelection).toBe("function")
    })

    test("exposes toggleAllSelection method", () => {
        const wrapper = i18nMount(KsTable, {
            props: {data: []},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).toggleAllSelection).toBe("function")
    })

    test("exposes clearSort method", () => {
        const wrapper = i18nMount(KsTable, {
            props: {data: []},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).clearSort).toBe("function")
    })

    test("exposes sort method", () => {
        const wrapper = i18nMount(KsTable, {
            props: {data: []},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).sort).toBe("function")
    })

    test("renders with columns", () => {
        const wrapper = i18nMount({
            components: {KsTable, KsTableColumn},
            template: `
                <ks-table :data="[{id: '1', name: 'Test'}]">
                    <ks-table-column prop="id" label="ID" />
                    <ks-table-column prop="name" label="Name" />
                </ks-table>
            `,
        }, {global: globalConfig})
        expect(wrapper.find(".kel-table").exists()).toBe(true)
    })
})
