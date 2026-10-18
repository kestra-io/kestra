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

const SECRET = {namespace: "company.team", key: "a-secret"}

/** Opens the Secrets list the way a page refresh does: the sort is already in the URL when the table mounts. */
async function mountSecretsList(query: LocationQueryRaw = {}) {
    const router: Router = createRouter({
        history: createMemoryHistory(),
        routes: [{name: "secrets/list", path: "/:tenant?/secrets", component: {template: "<div/>"}}],
    })
    await router.push({name: "secrets/list", params: {tenant: "main"}, query})
    await router.isReady()

    const wrapper = i18nMount(SecretsTable, {
        global: {
            plugins: [createPinia(), router, KestraDesignSystem],
            stubs: {KSFilter: true},
        },
    })
    await flushPromises()
    return {wrapper, router}
}

/** The column headers that announce a sort, as `aria-sort` exposes it to assistive technology. */
const sortedColumns = (wrapper: VueWrapper) => Object.fromEntries(
    wrapper.findAll("th")
        .filter((th) => th.attributes("aria-sort"))
        .map((th) => [th.text(), th.attributes("aria-sort")]),
)

const clickSort = async (wrapper: VueWrapper, label: string) => {
    await wrapper.get(`button[aria-label="Sort by ${label}"]`).trigger("click")
    await flushPromises()
}

const requestedSorts = () => listSecrets.mock.calls.map(([params]) => params?.sort)

describe("SecretsTable sort indicator", () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        listSecrets.mockReset()
        listSecrets.mockResolvedValue({readOnly: false, results: [SECRET], total: 1})
    })

    afterEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
    })

    it("sorts by key, ascending, when the URL has no sort", async () => {
        const {wrapper} = await mountSecretsList()

        expect(sortedColumns(wrapper)).toEqual({key: "ascending"})
        expect(requestedSorts()).toEqual([["key:asc"]])
    })

    it("shows the sort from the URL after a refresh, matching the order the rows were loaded in", async () => {
        const {wrapper} = await mountSecretsList({sort: "key:desc"})

        expect(sortedColumns(wrapper)).toEqual({key: "descending"})
        expect(requestedSorts()).toEqual([["key:desc"]])
    })

    it("reverses the restored sort on the first click of its column", async () => {
        const {wrapper, router} = await mountSecretsList({sort: "key:desc"})

        await clickSort(wrapper, "key")

        expect(router.currentRoute.value.query.sort).toBe("key:asc")
        expect(sortedColumns(wrapper)).toEqual({key: "ascending"})
        expect(requestedSorts().at(-1)).toEqual(["key:asc"])
    })

    it("shows the namespace sort from the URL with its direction", async () => {
        const {wrapper} = await mountSecretsList({sort: "namespace:desc"})

        expect(sortedColumns(wrapper)).toEqual({namespace: "descending"})
        expect(requestedSorts()).toEqual([["namespace:desc"]])
    })
})
