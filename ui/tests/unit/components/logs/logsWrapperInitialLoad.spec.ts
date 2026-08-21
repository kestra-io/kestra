import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"
import {createMemoryHistory, createRouter, type Router} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"

const searchLogs = vi.fn()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs: (...args: any[]) => searchLogs(...args),
    deleteLogsFromFlow: vi.fn(),
}))

import LogsWrapper from "../../../../src/components/logs/LogsWrapper.vue"

const LOG = {
    level: "INFO",
    namespace: "ns",
    flowId: "flow",
    executionId: "execution",
    thread: "thread",
    index: 0,
    attemptNumber: 0,
    executionKind: "flow",
    timestamp: "2026-06-02T08:00:00Z",
    message: "a log line",
}

const listSearches = () => searchLogs.mock.calls
    .map(([params]) => params)
    .filter((params) => params.size !== 1)

const levelOf = (params: any) => params.filters
    ?.find((filter: any) => filter.field === "level")?.value

function mountLogsWrapper(router: Router) {
    return mount(LogsWrapper, {
        global: {
            plugins: [
                createI18n({legacy: false, locale: "en", messages: {en: {}}}),
                createPinia(),
                router,
                KestraDesignSystem,
            ],
            stubs: {Sections: true, TopNavBar: true},
        },
    })
}

const settle = async () => {
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 200))
    await flushPromises()
}

describe("LogsWrapper initial load", () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        searchLogs.mockReset()
        searchLogs.mockResolvedValue({results: [LOG], total: 1})
    })

    afterEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        document.title = ""
    })

    it("searches once, with the default level, instead of first querying without it", async () => {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{name: "logs/list", path: "/:tenant?/logs", component: {template: "<div/>"}}],
        })
        await router.push({name: "logs/list", params: {tenant: "main"}})
        await router.isReady()

        const wrapper = mountLogsWrapper(router)
        await settle()

        expect(listSearches().map(levelOf)).toEqual(["INFO"])
        expect(wrapper.text()).toContain("a log line")
    })

    it("still reloads when the level filter changes afterwards", async () => {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{name: "logs/list", path: "/:tenant?/logs", component: {template: "<div/>"}}],
        })
        await router.push({name: "logs/list", params: {tenant: "main"}})
        await router.isReady()

        const wrapper = mountLogsWrapper(router)
        await settle()

        await (wrapper.vm as any).selectLevel("WARN")
        await settle()

        expect(listSearches().map(levelOf)).toEqual(["INFO", "WARN"])
    })

    it("searches anyway when the navigation writing the default never lands", async () => {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{name: "logs/list", path: "/:tenant?/logs", component: {template: "<div/>"}}],
        })
        await router.push({name: "logs/list", params: {tenant: "main"}})
        await router.isReady()
        // Stands in for anything that keeps the default out of the URL for good — a route guard
        // rejecting the navigation, or one that supersedes it and drops the level again.
        router.beforeEach((to, _from, next) => {
            next(!to.query["filters[level][GREATER_THAN_OR_EQUAL_TO]"])
        })

        const wrapper = mountLogsWrapper(router)
        await settle()
        // The gate gives up 2s after mount rather than leaving the page blank.
        await new Promise((resolve) => setTimeout(resolve, 2200))
        await flushPromises()

        expect(listSearches()).toHaveLength(1)
        expect(wrapper.text()).toContain("a log line")
    })
})
