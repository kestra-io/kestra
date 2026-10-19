import {describe, test, expect} from "vitest"
import KsTable from "./KsTable.vue"
import KsTableColumn from "./KsTableColumn.vue"
import {i18nMount} from "../../../../tests/units/i18nMount"

type KsTableExposedMethods = {
    clearSelection: () => void
    toggleAllSelection: () => void
    clearSort: () => void
    sort: (prop: string, order: string) => void
}

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
        expect(typeof (wrapper.vm as typeof wrapper.vm & KsTableExposedMethods).clearSelection).toBe("function")
    })

    test("exposes toggleAllSelection method", () => {
        const wrapper = i18nMount(KsTable, {
            props: {data: []},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as typeof wrapper.vm & KsTableExposedMethods).toggleAllSelection).toBe("function")
    })

    test("exposes clearSort method", () => {
        const wrapper = i18nMount(KsTable, {
            props: {data: []},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as typeof wrapper.vm & KsTableExposedMethods).clearSort).toBe("function")
    })

    test("exposes sort method", () => {
        const wrapper = i18nMount(KsTable, {
            props: {data: []},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as typeof wrapper.vm & KsTableExposedMethods).sort).toBe("function")
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
