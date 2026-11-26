import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"
import type {listAllKeys as SdkListAllKeys} from "@kestra-io/kestra-sdk/kv"
import en from "../../translations/en.json"
import {useNamespacesStore} from "override/stores/namespaces"

const listAllKeys = vi.fn<typeof SdkListAllKeys>()

vi.mock("../../utils/toast", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../utils/toast")>(),
    useToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), confirm: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/kv", () => ({
    listAllKeys: (...args: Parameters<typeof SdkListAllKeys>) => listAllKeys(...args),
}))

import KVTable from "./KVTable.vue"

const mounted: VueWrapper[] = []

async function openCreateDrawer() {
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
    mounted.push(wrapper)
    await flushPromises()
    const store = useNamespacesStore()
    const createKv = vi.spyOn(store, "createKv").mockResolvedValue(undefined as never)
    store.addKvModalVisible = true
    await flushPromises()
    return {wrapper, createKv}
}

async function save(wrapper: Awaited<ReturnType<typeof openCreateDrawer>>["wrapper"], description: string) {
    await wrapper.get("[data-test='kv-key'] input").setValue("desc_test")
    await wrapper.get("[data-test='kv-value'] textarea, [data-test='kv-value'] input").setValue("v")
    await wrapper.get("[data-test='kv-description'] textarea, [data-test='kv-description'] input").setValue(description)
    await wrapper.get("[data-test='kv-save']").trigger("click")
    await flushPromises()
}

describe("KVTable description", () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        listAllKeys.mockReset()
        listAllKeys.mockResolvedValue({results: [], total: 0})
    })

    afterEach(() => {
        while (mounted.length) mounted.pop()?.unmount()
    })

    it("explains why a description with emoji or other scripts cannot be saved instead of failing silently", async () => {
        const {wrapper, createKv} = await openCreateDrawer()

        await save(wrapper, "rocket 🚀 日本語")

        expect(createKv).not.toHaveBeenCalled()
        await vi.waitFor(() => expect(wrapper.get("[data-test='kv-description']").text()).toContain("only contain Latin characters"))
    })

    it("still saves a description with accented Latin letters", async () => {
        const {wrapper, createKv} = await openCreateDrawer()

        await save(wrapper, "café crème")

        await vi.waitFor(() => expect(createKv).toHaveBeenCalledWith(expect.objectContaining({description: "café crème"})))
    })
})
