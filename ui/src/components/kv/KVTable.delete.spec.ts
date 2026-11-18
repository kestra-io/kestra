import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"
import type {KvEntry} from "@kestra-io/kestra-sdk"
import type {deleteKeyValues as SdkDeleteKeyValues, listAllKeys as SdkListAllKeys} from "@kestra-io/kestra-sdk/kv"
import en from "../../translations/en.json"
import {useAuthStore, type Me} from "override/stores/auth"
import type {useToast} from "../../utils/toast"

const listAllKeys = vi.fn<typeof SdkListAllKeys>()
const deleteKeyValues = vi.fn<typeof SdkDeleteKeyValues>()

vi.mock("@kestra-io/kestra-sdk/kv", () => ({
    listAllKeys: (...args: Parameters<typeof SdkListAllKeys>) => listAllKeys(...args),
    deleteKeyValues: (...args: Parameters<typeof SdkDeleteKeyValues>) => deleteKeyValues(...args),
}))

const toast = {
    confirm: vi.fn((_message: string, callback: () => unknown) => callback()),
    success: vi.fn(),
    deleted: vi.fn(),
}

vi.mock(import("../../utils/toast"), async (importOriginal) => ({...await importOriginal(), useToast: () => toast as unknown as ReturnType<typeof useToast>}))

import KVTable from "./KVTable.vue"

async function mountKvList(rows: KvEntry[]) {
    listAllKeys.mockResolvedValue({results: rows, total: rows.length})
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{name: "kv/list", path: "/:tenant?/kv", component: {template: "<div/>"}}],
    })
    await router.push({name: "kv/list", params: {tenant: "main"}})
    await router.isReady()

    const wrapper = i18nMount(KVTable, {
        locales: en,
        global: {
            plugins: [createPinia(), router, KestraDesignSystem],
            stubs: {KsFilter: {name: "KsFilter", props: ["prefix"], template: "<div />"}},
        },
    })
    await flushPromises()
    return wrapper
}

const clickButton = async (wrapper: VueWrapper, text: string) => {
    const button = wrapper.findAll("button").find((b) => b.text() === text)
    if (!button) throw new Error(`No "${text}" button.`)
    await button.trigger("click")
    await flushPromises()
}

describe("KVTable delete prompts", () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        listAllKeys.mockReset()
        deleteKeyValues.mockReset()
        deleteKeyValues.mockResolvedValue({keys: []})
        Object.values(toast).forEach((fn) => fn.mockClear())
    })

    it("names the key in the single delete confirmation", async () => {
        const wrapper = await mountKvList([{namespace: "company.team", key: "api-token", description: "", updateDate: "2026-10-01T08:00:00Z"}])

        await wrapper.get("button[aria-label=\"Delete\"]").trigger("click")

        expect(toast.confirm.mock.calls[0][0]).toBe("Are you sure you want to delete <code>api-token</code>?")
    })

    it("names the skipped namespaces and the deleted count per namespace in a bulk delete", async () => {
        const wrapper = await mountKvList([
            {namespace: "company.team", key: "a", description: "", updateDate: "2026-10-01T08:00:00Z"},
            {namespace: "company.team", key: "b", description: "", updateDate: "2026-10-01T08:00:00Z"},
            {namespace: "company.other", key: "c", description: "", updateDate: "2026-10-01T08:00:00Z"},
        ])
        useAuthStore().user = {isAllowed: (_permission: string, _action: string, namespace?: string) => namespace !== "company.other"} as Me

        for (const checkbox of wrapper.findAll("tbody input[type=\"checkbox\"]")) {
            await checkbox.setValue(true)
        }
        await flushPromises()
        await clickButton(wrapper, "Delete")

        expect(toast.confirm.mock.calls[0][0]).toBe(
            "Are you sure you want to delete: <code>2</code> KV(s)?\n" +
            "You don't have permissions for those namespaces so they would be omitted: <code>company.other</code>",
        )
        expect(deleteKeyValues).toHaveBeenCalledExactlyOnceWith({namespace: "company.team", keys: ["a", "b"]})
        expect(toast.success).toHaveBeenCalledWith("<code>2</code> KV(s) deleted from the <code>company.team</code> namespace", "Successfully deleted")
    })
})
