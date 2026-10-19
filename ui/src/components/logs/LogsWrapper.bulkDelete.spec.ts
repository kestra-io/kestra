import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import KestraDesignSystem, {KsMessageBox} from "@kestra-io/design-system"

interface SearchLogsParams {
    size?: number
    filters?: {field: string; value: string}[]
}

const searchLogs = vi.fn()
const deleteLogsByIds = vi.fn()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs: (...args: unknown[]) => searchLogs(...args),
    deleteLogsByIds: (...args: unknown[]) => deleteLogsByIds(...args),
    deleteLogsByQuery: vi.fn(),
    deleteLogsFromFlow: vi.fn(),
}))

vi.mock("../../utils/toast", () => ({useToast: () => ({deleted: vi.fn(), success: vi.fn(), error: vi.fn()})}))

import LogsWrapper from "./LogsWrapper.vue"

const LOG_ID = "row-key-1"

const settle = async () => {
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 200))
    await flushPromises()
}

async function mountWithOneInfoLog() {
    let deleted = false
    searchLogs.mockImplementation((params: SearchLogsParams) => {
        if (params.size === 1) {
            const level = params.filters?.find((f) => f.field === "level")?.value
            const total = !deleted && ["TRACE", "DEBUG", "INFO"].includes(level ?? "") ? 1 : 0
            return Promise.resolve({total})
        }
        const results = deleted ? [] : [{
            id: LOG_ID,
            level: "INFO",
            namespace: "ns",
            flowId: "flow",
            executionId: "execution",
            thread: "thread",
            attemptNumber: 0,
            timestamp: "2026-06-02T08:00:00Z",
            message: "hello",
        }]
        return Promise.resolve({results, total: results.length, type: "OFFSET"})
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
    const wrapper = i18nMount(LogsWrapper, {
        global: {
            plugins: [createPinia(), router, KestraDesignSystem],
            stubs: {Sections: {template: "<div/>", methods: {refreshCharts: () => undefined}}, TopNavBar: true},
        },
    })
    await settle()
    return wrapper
}

const levelCountCalls = () => searchLogs.mock.calls.filter(([params]) => (params as SearchLogsParams).size === 1).length

describe("LogsWrapper bulk delete", () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        searchLogs.mockReset()
        deleteLogsByIds.mockReset()
        vi.spyOn(KsMessageBox, "confirm").mockResolvedValue("confirm" as never)
    })

    afterEach(() => {
        vi.restoreAllMocks()
        window.sessionStorage.clear()
        window.localStorage.clear()
        document.title = ""
    })

    it("does not render the row key as a log meta", async () => {
        const wrapper = await mountWithOneInfoLog()

        expect(wrapper.find(".log-row").exists()).toBe(true)
        expect(wrapper.text()).not.toContain(LOG_ID)
    })

    it("recounts the level chips once the selected logs are deleted", async () => {
        const wrapper = await mountWithOneInfoLog()
        const countsBeforeDelete = levelCountCalls()

        await wrapper.find(".log-row__checkbox input").setValue(true)
        await settle()
        const deleteButton = wrapper.findAll(".ks-bulk-select button").find((button) => button.text().trim() === "delete")
        await deleteButton!.trigger("click")
        await settle()

        expect(deleteLogsByIds).toHaveBeenCalledWith({body: [LOG_ID]})
        expect(levelCountCalls()).toBeGreaterThan(countsBeforeDelete)
    })
})
