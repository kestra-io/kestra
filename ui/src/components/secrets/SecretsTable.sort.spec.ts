import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

import {createPinia} from "pinia"
import {createMemoryHistory, createRouter, type LocationQueryRaw, type Router} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"
import type {listSecrets as SdkListSecrets} from "@kestra-io/kestra-sdk/secrets"

const listSecrets = vi.fn<typeof SdkListSecrets>()

vi.mock("@kestra-io/kestra-sdk/secrets", () => ({
    listSecrets: (...args: Parameters<typeof SdkListSecrets>) => listSecrets(...args),
}))

import SecretsTable from "./SecretsTable.vue"

/** Opens the secrets list the way a page refresh does: the sort is already in the URL when the table mounts. */
async function mountSecretsList(query: LocationQueryRaw = {}) {
    const router: Router = createRouter({
        history: createMemoryHistory(),
        routes: [{name: "secrets/list", path: "/:tenant?/secrets", component: {template: "<div/>"}}],
    })
    await router.push({name: "secrets/list", params: {tenant: "main"}, query})
    await router.isReady()

    const wrapper = i18nMount(SecretsTable, {
        props: {keyOnly: true},
        global: {
            plugins: [createPinia(), router, KestraDesignSystem],
            stubs: {KsFilter: true},
        },
    })
    await flushPromises()
    return {wrapper, router}
}

const sortedColumns = (wrapper: VueWrapper) => Object.fromEntries(
    wrapper.findAll("th")
        .filter((th) => th.attributes("aria-sort"))
        .map((th) => [th.text(), th.attributes("aria-sort")]),
)

const requestedSorts = () => listSecrets.mock.calls.map(([params]) => params?.sort)

describe("SecretsTable sort indicator", () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        listSecrets.mockReset()
        listSecrets.mockResolvedValue({results: [{key: "A_SECRET"}], total: 1, readOnly: false})
    })

    afterEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
    })

    it("sorts by key, ascending, when the URL has no sort", async () => {
        const {wrapper} = await mountSecretsList()

        expect(Object.values(sortedColumns(wrapper))).toEqual(["ascending"])
        expect(requestedSorts()).toEqual([["key:asc"]])
    })

    it("shows the sort from the URL after a refresh and reverses it on the first click", async () => {
        const {wrapper, router} = await mountSecretsList({sort: "key:desc"})

        expect(Object.values(sortedColumns(wrapper))).toEqual(["descending"])
        expect(requestedSorts()).toEqual([["key:desc"]])

        await wrapper.get("th[aria-sort] button[aria-label^=\"Sort by\"]").trigger("click")
        await flushPromises()

        expect(router.currentRoute.value.query.sort).toBe("key:asc")
        expect(Object.values(sortedColumns(wrapper))).toEqual(["ascending"])
        expect(requestedSorts().at(-1)).toEqual(["key:asc"])
    })
})
