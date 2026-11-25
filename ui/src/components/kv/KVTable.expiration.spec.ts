import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"
import type {listAllKeys as SdkListAllKeys} from "@kestra-io/kestra-sdk/kv"
import en from "../../translations/en.json"
import {useNamespacesStore} from "override/stores/namespaces"
import DateSelect from "../executions/date-select/DateSelect.vue"

const listAllKeys = vi.fn<typeof SdkListAllKeys>()

vi.mock("@kestra-io/kestra-sdk/kv", () => ({
    listAllKeys: (...args: Parameters<typeof SdkListAllKeys>) => listAllKeys(...args),
}))

import KVTable from "./KVTable.vue"

describe("KVTable expiration field", () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        listAllKeys.mockReset()
        listAllKeys.mockResolvedValue({results: [], total: 0})
    })

    it("does not show custom duration box when Never is selected in Create KV drawer", async () => {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{name: "kv/list", path: "/:tenant?/kv", component: {template: "<div/>"}}],
        })
        await router.push({name: "kv/list", params: {tenant: "main"}})
        await router.isReady()

        const wrapper = i18nMount(KVTable, {
            locales: en,
            props: {
                namespace: "company.team",
            },
            global: {
                plugins: [createPinia(), router, KestraDesignSystem],
                stubs: {
                    KsFilter: {name: "KsFilter", props: ["prefix"], template: "<div />"},
                    KsDrawer: {template: "<div><slot /><slot name='footer' /></div>"},
                },
            },
        })
        await flushPromises()

        useNamespacesStore().addKvModalVisible = true
        await flushPromises()

        const ttlField = wrapper.get("[data-test='kv-ttl']")
        const dateSelect = ttlField.getComponent(DateSelect)
        expect(dateSelect.props("value")).toBe("never")
        expect(ttlField.find("[data-test='custom-duration']").exists()).toBe(false)

        await dateSelect.vm.$emit("change", "custom")
        await flushPromises()
        expect(ttlField.find("[data-test='custom-duration']").exists()).toBe(true)

        await dateSelect.vm.$emit("change", "never")
        await flushPromises()
        expect(ttlField.find("[data-test='custom-duration']").exists()).toBe(false)
    })
})
