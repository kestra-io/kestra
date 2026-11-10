import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, type Pinia} from "pinia"
import {createMemoryHistory, createRouter, type Router} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {useLogsStore, useScopedLogsStore} from "../../stores/logs"
import LogsWrapper from "./LogsWrapper.vue"

interface SearchLogsParams {
    size?: number;
    cursor?: string;
    filters?: {field: string; value?: unknown}[];
}

const searchLogs = vi.fn()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs: (...args: unknown[]) => searchLogs(...args),
    deleteLogsFromFlow: vi.fn(),
}))

const logWith = (triggerId: string) => ({
    level: "INFO",
    namespace: "company.team",
    flowId: "flow",
    triggerId,
    thread: "thread",
    timestamp: "2026-06-02T08:00:00Z",
    message: `${triggerId} evaluated`,
})

const responseFor = (triggerId: string) => ({results: [logWith(triggerId)], total: 1})

function mountLogsWrapper(pinia: Pinia, router: Router, triggerId: string, scope?: string) {
    return i18nMount(LogsWrapper, {
        props: {
            embed: true,
            withCharts: false,
            filters: {"filters[triggerId][EQUALS]": triggerId},
            logsStoreScope: scope,
        },
        global: {
            plugins: [pinia, router, KestraDesignSystem],
            stubs: {Sections: true, TopNavBar: true},
        },
    })
}

const settle = async () => {
    await flushPromises()
    await new Promise(resolve => setTimeout(resolve, 200))
    await flushPromises()
}

describe("LogsWrapper scoped stores", () => {
    let pinia: Pinia
    let router: Router

    beforeEach(async () => {
        sessionStorage.clear()
        localStorage.clear()
        searchLogs.mockReset()
        pinia = createPinia()
        router = createRouter({
            history: createMemoryHistory(),
            routes: [{name: "flows/update/triggers", path: "/:tenant/flows/:namespace/:id/triggers", component: {template: "<div/>"}}],
        })
        await router.push({name: "flows/update/triggers", params: {tenant: "main", namespace: "company.team", id: "flow"}})
        await router.isReady()
    })

    afterEach(() => {
        sessionStorage.clear()
        localStorage.clear()
        document.title = ""
    })

    it("keeps concurrent trigger searches separate when the first request finishes last", async () => {
        let resolveHigh!: (response: ReturnType<typeof responseFor>) => void
        let resolveMid!: (response: ReturnType<typeof responseFor>) => void
        const highResponse = new Promise<ReturnType<typeof responseFor>>(resolve => { resolveHigh = resolve })
        const midResponse = new Promise<ReturnType<typeof responseFor>>(resolve => { resolveMid = resolve })
        searchLogs.mockImplementation((params: SearchLogsParams) => {
            if (params.size === 1) return Promise.resolve({results: [], total: 0})
            const triggerId = params.filters?.find(filter => filter.field === "triggerId")?.value
            if (triggerId === "high-prio") return highResponse
            if (triggerId === "mid-prio") return midResponse
            throw new Error("Missing trigger filter")
        })

        const high = mountLogsWrapper(pinia, router, "high-prio", "high")
        const mid = mountLogsWrapper(pinia, router, "mid-prio", "mid")
        await settle()
        expect(searchLogs.mock.calls.filter(([params]) => params.size !== 1)).toHaveLength(2)

        resolveMid(responseFor("mid-prio"))
        await settle()
        expect(mid.text()).toContain("mid-prio evaluated")
        expect(high.text()).not.toContain("mid-prio evaluated")

        resolveHigh(responseFor("high-prio"))
        await settle()
        expect(high.text()).toContain("high-prio evaluated")
        expect(high.text()).not.toContain("mid-prio evaluated")
        expect(mid.text()).toContain("mid-prio evaluated")
        expect(mid.text()).not.toContain("high-prio evaluated")
    })

    it("pages one trigger without changing another trigger's rows or cursors", async () => {
        searchLogs.mockImplementation((params: SearchLogsParams) => {
            if (params.size === 1) return Promise.resolve({results: [], total: 0})
            const triggerId = params.filters?.find(filter => filter.field === "triggerId")?.value
            if (triggerId === "high-prio") {
                return Promise.resolve({
                    ...responseFor(params.cursor ? "high-prio page two" : "high-prio"),
                    type: "CURSOR",
                    nextCursor: params.cursor ? undefined : "high-next",
                })
            }
            if (triggerId === "mid-prio") return Promise.resolve({...responseFor("mid-prio"), type: "CURSOR"})
            throw new Error("Missing trigger filter")
        })
        const high = mountLogsWrapper(pinia, router, "high-prio", "high")
        const mid = mountLogsWrapper(pinia, router, "mid-prio", "mid")
        await settle()
        expect(high.find("[aria-label=\"next\"]").exists()).toBe(true)
        expect(mid.find("[aria-label=\"next\"]").exists()).toBe(false)

        await high.find("[aria-label=\"next\"]").trigger("click")
        await settle()
        expect(high.text()).toContain("high-prio page two evaluated")
        expect(mid.text()).toContain("mid-prio evaluated")
        expect(mid.text()).not.toContain("high-prio")
        expect(mid.find("[aria-label=\"previous\"]").exists()).toBe(false)

        await high.find("[aria-label=\"previous\"]").trigger("click")
        await settle()
        expect(high.text()).toContain("high-prio evaluated")
        expect(high.text()).not.toContain("page two")
        expect(mid.text()).toContain("mid-prio evaluated")
    })

    it("keeps unscoped wrappers on the shared store after unmount", async () => {
        searchLogs.mockResolvedValue(responseFor("shared"))
        const wrapper = mountLogsWrapper(pinia, router, "shared")
        await settle()

        expect(useLogsStore(pinia).logs).toEqual([logWith("shared")])
        expect(wrapper.text()).toContain("shared evaluated")
        wrapper.unmount()
        expect(useLogsStore(pinia).logs).toEqual([logWith("shared")])
    })

    it("removes a collapsed panel's state and reopens it with fresh state", async () => {
        searchLogs.mockResolvedValue(responseFor("high-prio"))
        const wrapper = mountLogsWrapper(pinia, router, "high-prio", "high")
        await settle()
        const previousStore = useScopedLogsStore("high")
        expect(previousStore.logs).toEqual([logWith("high-prio")])

        wrapper.unmount()
        expect(pinia.state.value).not.toHaveProperty(previousStore.$id)
        const nextStore = useScopedLogsStore("high")
        expect(nextStore).not.toBe(previousStore)
        expect(nextStore.logs).toBeUndefined()
        nextStore.$dispose()
        delete pinia.state.value[nextStore.$id]
    })
})
