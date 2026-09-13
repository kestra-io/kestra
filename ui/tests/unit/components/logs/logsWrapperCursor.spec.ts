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
import LogLevelNavigator from "../../../../src/components/logs/LogLevelNavigator.vue"

const logWith = (message: string) => ({
    level: "INFO",
    namespace: "ns",
    flowId: "flow",
    executionId: "execution",
    thread: "thread",
    index: 0,
    attemptNumber: 0,
    executionKind: "flow",
    timestamp: "2026-06-02T08:00:00Z",
    message,
})

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

async function mountAtLogsList() {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{name: "logs/list", path: "/:tenant?/logs", component: {template: "<div/>"}}],
    })
    await router.push({name: "logs/list", params: {tenant: "main"}})
    await router.isReady()
    const wrapper = mountLogsWrapper(router)
    await settle()
    return wrapper
}

const settle = async () => {
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 200))
    await flushPromises()
}

describe("LogsWrapper cursor pagination", () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        searchLogs.mockReset()
    })

    afterEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        document.title = ""
    })

    // A two-page cursor store: page 0 (p0) → page 1 (p1, last page with data) → empty.
    const useTwoPageCursorStore = () => {
        searchLogs.mockImplementation((params: any) => {
            if (params.size === 1) return Promise.resolve({results: [], total: 0})
            switch (params.cursor) {
            case undefined:
                return Promise.resolve({results: [logWith("p0")], type: "CURSOR", nextCursor: "tok-1"})
            case "tok-1":
                return Promise.resolve({results: [logWith("p1")], type: "CURSOR", nextCursor: "tok-2"})
            default:
                return Promise.resolve({results: [], type: "CURSOR"})
            }
        })
    }

    it("clicking Next on the last page keeps the rows and drops the Next control", async () => {
        useTwoPageCursorStore()
        const wrapper = await mountAtLogsList()

        // Advance to the last page with data (p1), which still advertises a cursor.
        await wrapper.find("[aria-label=\"next\"]").trigger("click")
        await settle()
        expect(wrapper.text()).toContain("p1")
        expect(wrapper.find("[aria-label=\"next\"]").exists()).toBe(true)

        // The next fetch comes back empty: keep p1 on screen, hide Next, no empty state.
        await wrapper.find("[aria-label=\"next\"]").trigger("click")
        await settle()
        expect(wrapper.text()).toContain("p1")
        expect(wrapper.find("[aria-label=\"next\"]").exists()).toBe(false)
    })

    it("exposes a working Previous once the user has paged forward", async () => {
        useTwoPageCursorStore()
        const wrapper = await mountAtLogsList()
        expect(wrapper.find("[aria-label=\"previous\"]").exists()).toBe(false)

        await wrapper.find("[aria-label=\"next\"]").trigger("click")
        await settle()
        expect(wrapper.text()).toContain("p1")
        expect(wrapper.find("[aria-label=\"previous\"]").exists()).toBe(true)

        await wrapper.find("[aria-label=\"previous\"]").trigger("click")
        await settle()
        expect(wrapper.text()).toContain("p0")
        expect(wrapper.find("[aria-label=\"previous\"]").exists()).toBe(false)
    })

    it("hides the chart toggle in cursor mode but keeps it in offset mode", async () => {
        useTwoPageCursorStore()
        const cursorWrapper = await mountAtLogsList()
        expect((cursorWrapper.vm as any).logTableOptions.chart.shown).toBe(false)

        searchLogs.mockReset()
        searchLogs.mockResolvedValue({results: [logWith("offset")], total: 1, type: "OFFSET"})
        const offsetWrapper = await mountAtLogsList()
        expect((offsetWrapper.vm as any).logTableOptions.chart.shown).toBe(true)
    })

    // Cursor stores can't produce per-level counts, so the level quick-filter chips are hidden by
    // design in cursor mode; level filtering stays available from the main filter bar. Offset mode
    // (below) proves the row isn't hidden for some unrelated reason.
    it("hides the level-navigator chips in cursor mode", async () => {
        useTwoPageCursorStore()
        const wrapper = await mountAtLogsList()

        expect(wrapper.findAllComponents(LogLevelNavigator)).toHaveLength(0)
    })

    it("shows the level-navigator chips in offset mode when counts are available", async () => {
        const countsByLevel: Record<string, number> = {TRACE: 5, DEBUG: 5, INFO: 5, WARN: 2, ERROR: 0}
        searchLogs.mockImplementation((params: any) => {
            if (params.size === 1) {
                const level = params.filters?.find((f: any) => f.field === "level")?.value
                return Promise.resolve({total: countsByLevel[level] ?? 0})
            }
            return Promise.resolve({results: [logWith("offset")], total: 1, type: "OFFSET"})
        })
        const wrapper = await mountAtLogsList()

        expect(wrapper.findAllComponents(LogLevelNavigator).length).toBeGreaterThan(0)
    })
})
