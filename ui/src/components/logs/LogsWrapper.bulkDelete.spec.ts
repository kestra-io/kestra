import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

import {createPinia} from "pinia"
import {useLogsStore} from "../../stores/logs"
import {createMemoryHistory, createRouter} from "vue-router"
import KestraDesignSystem, {KsMessageBox} from "@kestra-io/design-system"

interface SearchLogsParams {
    size?: number
    filters?: {field: string; value: string}[]
}

const searchLogs = vi.fn()
const deleteLogsByIds = vi.fn()
const deleteLogsByQuery = vi.fn()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs: (...args: unknown[]) => searchLogs(...args),
    deleteLogsByIds: (...args: unknown[]) => deleteLogsByIds(...args),
    deleteLogsByQuery: (...args: unknown[]) => deleteLogsByQuery(...args),
    deleteLogsFromFlow: vi.fn(),
}))

vi.mock("../../utils/toast", () => ({useToast: () => ({deleted: vi.fn(), success: vi.fn(), error: vi.fn()})}))

import LogsWrapper from "./LogsWrapper.vue"

const LOG_ID = "row-key-1"
const OTHER_LOG_ID = "row-key-2"

const settle = async () => {
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 200))
    await flushPromises()
}

const logEntry = (id: string) => ({
    id,
    level: "INFO" as const,
    namespace: "ns",
    flowId: "flow",
    executionId: "execution",
    thread: "thread",
    attemptNumber: 0,
    timestamp: "2026-06-02T08:00:00Z",
    message: "hello",
})

async function mountWithLogs({ids = [LOG_ID], total = ids.length}: {ids?: string[]; total?: number} = {}) {
    let deleted = false
    searchLogs.mockImplementation((params: SearchLogsParams) => {
        if (params.size === 1) {
            const level = params.filters?.find((f) => f.field === "level")?.value
            const levelTotal = !deleted && ["TRACE", "DEBUG", "INFO"].includes(level ?? "") ? total : 0
            return Promise.resolve({total: levelTotal})
        }
        const results = deleted ? [] : ids.map(logEntry)
        return Promise.resolve({results, total: deleted ? 0 : total, type: "OFFSET"})
    })
    deleteLogsByIds.mockImplementation(() => {
        deleted = true
        return Promise.resolve({count: 1})
    })

    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{name: "logs/list", path: "/:tenant?/logs", component: {template: "<div/>"}}],
    })
    await router.push({name: "logs/list", params: {tenant: "main"}})
    await router.isReady()
    const pinia = createPinia()
    const wrapper = i18nMount(LogsWrapper, {
        global: {
            plugins: [pinia, router, KestraDesignSystem],
            stubs: {Sections: {template: "<div/>", methods: {refreshCharts: () => undefined}}, TopNavBar: true},
        },
    })
    await settle()
    return {wrapper, router, logsStore: useLogsStore(pinia)}
}

const levelCountCalls = () => searchLogs.mock.calls.filter(([params]) => (params as SearchLogsParams).size === 1).length

describe("LogsWrapper bulk delete", () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        searchLogs.mockReset()
        deleteLogsByIds.mockReset()
        deleteLogsByQuery.mockReset()
        deleteLogsByQuery.mockResolvedValue({count: 3})
        vi.spyOn(KsMessageBox, "confirm").mockResolvedValue("confirm" as never)
    })

    afterEach(() => {
        vi.restoreAllMocks()
        window.sessionStorage.clear()
        window.localStorage.clear()
        document.title = ""
    })

    it("does not render the row key as a log meta", async () => {
        const {wrapper} = await mountWithLogs()

        expect(wrapper.find(".log-row").exists()).toBe(true)
        expect(wrapper.text()).not.toContain(LOG_ID)
    })

    it("recounts the level chips once the selected logs are deleted", async () => {
        const {wrapper} = await mountWithLogs()
        const countsBeforeDelete = levelCountCalls()

        await wrapper.find("[data-test=log-row-checkbox] input").setValue(true)
        await settle()
        await wrapper.find("[data-test=logs-bulk-delete]").trigger("click")
        await settle()

        expect(deleteLogsByIds).toHaveBeenCalledWith({body: [LOG_ID]})
        expect(levelCountCalls()).toBeGreaterThan(countsBeforeDelete)
    })

    it("keeps the selection when the list reloads with the same logs and drops the ids that disappeared", async () => {
        const {wrapper, logsStore} = await mountWithLogs({ids: [LOG_ID, OTHER_LOG_ID]})
        const checkboxes = wrapper.findAll("[data-test=log-row-checkbox] input")
        await checkboxes[0].setValue(true)
        await checkboxes[1].setValue(true)

        logsStore.logs = [logEntry(LOG_ID), logEntry(OTHER_LOG_ID)]
        await settle()
        expect(wrapper.find("[data-test=logs-bulk-actions]").exists()).toBe(true)
        expect(wrapper.findAll(".log-row--selected")).toHaveLength(2)

        logsStore.logs = [logEntry(OTHER_LOG_ID)]
        await settle()
        expect(wrapper.findAll(".log-row--selected")).toHaveLength(1)

        logsStore.logs = [logEntry("unrelated")]
        await settle()
        expect(wrapper.find("[data-test=logs-bulk-actions]").exists()).toBe(false)
    })

    it("deletes by the current filters when every matching log is selected", async () => {
        const {wrapper} = await mountWithLogs({ids: [LOG_ID, OTHER_LOG_ID], total: 3})

        await wrapper.find("[data-test=log-row-checkbox] input").setValue(true)
        await settle()
        await wrapper.find("[data-test=bulk-select-all]").trigger("click")
        await wrapper.find("[data-test=logs-bulk-delete]").trigger("click")
        await settle()

        expect(deleteLogsByIds).not.toHaveBeenCalled()
        expect(deleteLogsByQuery).toHaveBeenCalledTimes(1)
        expect(deleteLogsByQuery.mock.calls[0][0].filters).toEqual(
            expect.arrayContaining([expect.objectContaining({field: "level"})]),
        )
    })

    it("clears a select-all choice when the filters change", async () => {
        const {wrapper, router} = await mountWithLogs({ids: [LOG_ID, OTHER_LOG_ID], total: 3})
        await wrapper.find("[data-test=log-row-checkbox] input").setValue(true)
        await settle()
        await wrapper.find("[data-test=bulk-select-all]").trigger("click")
        expect(wrapper.find("[data-test=logs-bulk-actions]").exists()).toBe(true)

        await router.push({query: {...router.currentRoute.value.query, "filters[flowId][EQUALS]": "other"}})
        await settle()

        expect(wrapper.find("[data-test=logs-bulk-actions]").exists()).toBe(false)
    })
})
