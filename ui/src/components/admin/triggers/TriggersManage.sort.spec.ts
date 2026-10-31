import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

import {createPinia} from "pinia"
import {createMemoryHistory, createRouter, type LocationQueryRaw, type Router} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"

const searchTriggers = vi.hoisted(() => vi.fn())

vi.mock("../../../utils/triggers", () => ({
    searchTriggers,
}))
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => false, hasAny: () => false, hasAnyAction: () => false}}),
}))
vi.mock("../../../stores/flow", () => ({useFlowStore: () => ({})}))
vi.mock("../../../stores/executions", () => ({useExecutionsStore: () => ({})}))

import TriggersManage from "./TriggersManage.vue"

const TRIGGER = {
    trigger: {id: "poll", type: "io.kestra.plugin.core.http.Trigger"},
    state: {
        namespace: "company.team",
        flowId: "my-flow",
        triggerId: "poll",
        lastTriggeredDate: "2026-10-01T08:00:00Z",
    },
}

/** Opens TriggersManage the way a page refresh does: the sort query is already in the URL when the component mounts. */
async function mountTriggers(query: LocationQueryRaw = {}) {
    const router: Router = createRouter({
        history: createMemoryHistory(),
        routes: [{name: "admin/triggers", path: "/:tenant/triggers", component: {template: "<div/>"}}],
    })
    await router.push({name: "admin/triggers", params: {tenant: "main"}, query})
    await router.isReady()

    const wrapper = i18nMount(TriggersManage, {
        global: {
            plugins: [createPinia(), router, KestraDesignSystem],
            stubs: {
                FlowRun: true,
                TopNavBar: true,
                AddTriggerModal: true,
                TestEventDialog: true,
                KsFilter: {template: "<div />", props: ["prefix"]},
            },
        },
    })
    await flushPromises()
    return {wrapper, router}
}

/** The column headers that announce a sort, as aria-sort exposes it to assistive technology. */
const sortedColumns = (wrapper: VueWrapper) => Object.fromEntries(
    wrapper.findAll("th")
        .filter((th) => th.attributes("aria-sort"))
        .map((th) => [th.text(), th.attributes("aria-sort")]),
)

const clickSort = async (wrapper: VueWrapper, label: string) => {
    await wrapper.get(`button[aria-label="Sort by ${label}"]`).trigger("click")
    await flushPromises()
}

const requestedSorts = () => searchTriggers.mock.calls.map(([params]) => params?.sort)

describe("TriggersManage sort indicator", () => {
    beforeEach(() => {
        localStorage.clear()
        sessionStorage.clear()
        searchTriggers.mockReset()
        searchTriggers.mockResolvedValue({
            total: 1,
            results: [TRIGGER],
        })
    })

    afterEach(() => {
        localStorage.clear()
        sessionStorage.clear()
    })

    it("initializes with default trigger ID ascending sort when no sort query is present", async () => {
        const {wrapper} = await mountTriggers()

        expect(sortedColumns(wrapper)).toEqual({id: "ascending"})
        expect(requestedSorts()).toEqual(["triggerId:asc"])
    })

    it("reverses the default sort on first click of the id column", async () => {
        const {wrapper, router} = await mountTriggers()

        await clickSort(wrapper, "id")

        expect(router.currentRoute.value.query.sort).toBe("triggerId:desc")
        expect(sortedColumns(wrapper)).toEqual({id: "descending"})
        expect(requestedSorts().at(-1)).toBe("triggerId:desc")
    })

    it("restores ascending sort from URL on refresh and passes it to loadData", async () => {
        const {wrapper} = await mountTriggers({sort: "namespace:asc"})

        expect(sortedColumns(wrapper)).toEqual({namespace: "ascending"})
        expect(requestedSorts()).toEqual(["namespace:asc"])
    })

    it("reverses the sort on first click and updates URL and data request", async () => {
        const {wrapper, router} = await mountTriggers({sort: "namespace:asc"})

        await clickSort(wrapper, "namespace")

        expect(router.currentRoute.value.query.sort).toBe("namespace:desc")
        expect(sortedColumns(wrapper)).toEqual({namespace: "descending"})
        expect(requestedSorts().at(-1)).toBe("namespace:desc")
    })

    it("restores descending sort from URL on refresh", async () => {
        const {wrapper} = await mountTriggers({sort: "lastTriggeredDate:desc"})

        expect(sortedColumns(wrapper)).toEqual({"last trigger date": "descending"})
        expect(requestedSorts()).toEqual(["lastTriggeredDate:desc"])
    })

    it("reverses a restored descending sort on first click", async () => {
        const {wrapper, router} = await mountTriggers({sort: "flowId:desc"})

        await clickSort(wrapper, "flow")

        expect(router.currentRoute.value.query.sort).toBe("flowId:asc")
        expect(sortedColumns(wrapper)).toEqual({flow: "ascending"})
        expect(requestedSorts().at(-1)).toBe("flowId:asc")
    })

    it.each([
        ["flowId:sideways", "flowId", "flow"],
        ["namespace:DESC", "namespace", "namespace"],
    ])("interprets %s as descending, matching backend convention", async (sort, _prop, label) => {
        const {wrapper} = await mountTriggers({sort})

        expect(sortedColumns(wrapper)).toEqual({[label]: "descending"})
        expect(requestedSorts()).toEqual([sort])
    })

    it.each([
        ["missing direction", "namespace"],
        ["empty direction", "namespace:"],
        ["empty column", ":asc"],
        ["only separator", ":"],
        ["extra separator", "namespace:desc:asc"],
        ["empty string", ""],
    ])("falls back safely to default trigger ID ascending sort for %s", async (_case, sort) => {
        const {wrapper} = await mountTriggers({sort})

        expect(sortedColumns(wrapper)).toEqual({id: "ascending"})
    })

    it("falls back safely to default trigger ID ascending sort when sort query is an array", async () => {
        const {wrapper} = await mountTriggers({sort: ["namespace:asc", "flowId:desc"]})

        expect(sortedColumns(wrapper)).toEqual({id: "ascending"})
    })
})
