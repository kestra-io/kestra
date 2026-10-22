import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

import {createPinia} from "pinia"
import {createMemoryHistory, createRouter, type LocationQueryRaw, type Router} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"
import type {KvEntry} from "@kestra-io/kestra-sdk"
import type {listAllKeys as SdkListAllKeys} from "@kestra-io/kestra-sdk/kv"

const listAllKeys = vi.fn<typeof SdkListAllKeys>()

vi.mock("@kestra-io/kestra-sdk/kv", () => ({
    listAllKeys: (...args: Parameters<typeof SdkListAllKeys>) => listAllKeys(...args),
}))

import KVTable from "./KVTable.vue"

const KV: KvEntry = {namespace: "company.team", key: "a-key", description: "", updateDate: "2026-10-01T08:00:00Z"}

/** Opens the KV list the way a page refresh does: the sort is already in the URL when the table mounts. */
async function mountKvList(query: LocationQueryRaw = {}) {
    const router: Router = createRouter({
        history: createMemoryHistory(),
        routes: [{name: "kv/list", path: "/:tenant?/kv", component: {template: "<div/>"}}],
    })
    await router.push({name: "kv/list", params: {tenant: "main"}, query})
    await router.isReady()

    const wrapper = i18nMount(KVTable, {
        global: {
            plugins: [createPinia(), router, KestraDesignSystem],
            // A bare stub forwards `prefix` to the DOM element, where it is a read-only property.
            stubs: {KsFilter: {name: "KsFilter", props: ["prefix"], template: "<div />"}},
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

const requestedSorts = () => listAllKeys.mock.calls.map(([params]) => params?.sort)

describe("KVTable sort indicator", () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        listAllKeys.mockReset()
        listAllKeys.mockResolvedValue({results: [KV], total: 1})
    })

    afterEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
    })

    it("sorts by key, ascending, when the URL has no sort", async () => {
        const {wrapper} = await mountKvList()

        expect(sortedColumns(wrapper)).toEqual({key: "ascending"})
        expect(requestedSorts()).toEqual([["name:asc"]])
    })

    it("shows the sort from the URL after a refresh, matching the order the rows were loaded in", async () => {
        const {wrapper} = await mountKvList({sort: "updateDate:desc"})

        expect(sortedColumns(wrapper)).toEqual({"last modified": "descending"})
        expect(requestedSorts()).toEqual([["updateDate:desc"]])
    })

    it("reverses the restored sort on the first click of its column", async () => {
        const {wrapper, router} = await mountKvList({sort: "updateDate:asc"})

        await clickSort(wrapper, "last modified")

        expect(router.currentRoute.value.query.sort).toBe("updateDate:desc")
        expect(sortedColumns(wrapper)).toEqual({"last modified": "descending"})
        expect(requestedSorts().at(-1)).toEqual(["updateDate:desc"])
    })

    it("reverses a restored descending sort on the first click too", async () => {
        const {wrapper, router} = await mountKvList({sort: "updateDate:desc"})

        await clickSort(wrapper, "last modified")

        expect(router.currentRoute.value.query.sort).toBe("updateDate:asc")
        expect(sortedColumns(wrapper)).toEqual({"last modified": "ascending"})
    })

    it("shows any other sortable column from the URL with its direction", async () => {
        const {wrapper} = await mountKvList({sort: "namespace:desc"})

        expect(sortedColumns(wrapper)).toEqual({namespace: "descending"})
        expect(requestedSorts()).toEqual([["namespace:desc"]])
    })

    it("moves the sort to another column, ascending first, when that column is clicked", async () => {
        const {wrapper, router} = await mountKvList({sort: "updateDate:desc"})

        await clickSort(wrapper, "key")

        expect(router.currentRoute.value.query.sort).toBe("key:asc")
        expect(sortedColumns(wrapper)).toEqual({key: "ascending"})
    })

    // The API sorts ascending only for the exact direction "asc", so anything else lists the rows descending.
    it.each(["updateDate:sideways", "updateDate:ASC"])("shows %s as descending, the order the API sorts the rows in", async (sort) => {
        const {wrapper} = await mountKvList({sort})

        expect(sortedColumns(wrapper)).toEqual({"last modified": "descending"})
        expect(requestedSorts()).toEqual([[sort]])
    })

    it("marks no column when the URL sorts by a field the table does not show", async () => {
        const {wrapper} = await mountKvList({sort: "creationDate:desc"})

        expect(sortedColumns(wrapper)).toEqual({})
        expect(requestedSorts()).toEqual([["creationDate:desc"]])
    })

    // The API answers each of these with a 422, so there is no row order for the arrow to agree with.
    it.each([
        ["no direction", "updateDate"],
        ["an empty direction", "updateDate:"],
        ["an empty field", ":desc"],
        ["only a separator", ":"],
        ["an extra separator", "updateDate:desc:asc"],
    ])("keeps the key, ascending default for a sort with %s", async (_case, sort) => {
        const {wrapper} = await mountKvList({sort})

        expect(sortedColumns(wrapper)).toEqual({key: "ascending"})
    })

    it("keeps the key, ascending default when the URL repeats the sort parameter", async () => {
        const {wrapper} = await mountKvList({sort: ["updateDate:desc", "key:asc"]})

        expect(sortedColumns(wrapper)).toEqual({key: "ascending"})
    })
})
