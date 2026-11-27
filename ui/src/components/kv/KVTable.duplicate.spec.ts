import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"
import type {keyValue as SdkKeyValue, listAllKeys as SdkListAllKeys} from "@kestra-io/kestra-sdk/kv"
import en from "../../translations/en.json"
import {useNamespacesStore} from "override/stores/namespaces"

const listAllKeys = vi.fn<typeof SdkListAllKeys>()
const keyValue = vi.fn<typeof SdkKeyValue>()

vi.mock("@kestra-io/kestra-sdk/kv", () => ({
    listAllKeys: (...args: Parameters<typeof SdkListAllKeys>) => listAllKeys(...args),
    keyValue: (...args: Parameters<typeof SdkKeyValue>) => keyValue(...args),
}))

import KVTable from "./KVTable.vue"

describe("KVTable create", () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        listAllKeys.mockReset()
        keyValue.mockReset()
        listAllKeys.mockResolvedValue({results: [{namespace: "company.team", key: "key01"}], total: 30})
    })

    it("refuses a key that exists on another page instead of overwriting it", async () => {
        keyValue.mockResolvedValue({type: "STRING"})
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{name: "kv/list", path: "/:tenant?/kv", component: {template: "<div/>"}}],
        })
        await router.push({name: "kv/list", params: {tenant: "main"}})
        await router.isReady()
        const wrapper = i18nMount(KVTable, {
            locales: en,
            props: {namespace: "company.team"},
            global: {
                plugins: [createPinia(), router, KestraDesignSystem],
                stubs: {
                    KsFilter: {name: "KsFilter", props: ["prefix"], template: "<div />"},
                    KsDrawer: {template: "<div><slot /><slot name='footer' /></div>"},
                },
            },
        })
        await flushPromises()
        const store = useNamespacesStore()
        const createKv = vi.spyOn(store, "createKv").mockResolvedValue(undefined as never)
        store.addKvModalVisible = true
        await flushPromises()

        await wrapper.get("[data-test='kv-key'] input").setValue("key30")
        await wrapper.get("[data-test='kv-value'] textarea, [data-test='kv-value'] input").setValue("NEW")
        await wrapper.get("[data-test='kv-save']").trigger("click")
        await flushPromises()

        expect(keyValue).toHaveBeenCalledWith({namespace: "company.team", key: "key30"})
        expect(createKv).not.toHaveBeenCalled()
        await vi.waitFor(() => expect(wrapper.get("[data-test='kv-key']").text()).toContain("This key already exists"))
    })
})
