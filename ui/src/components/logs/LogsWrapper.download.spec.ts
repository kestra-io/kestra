import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import KestraDesignSystem, {dayjs} from "@kestra-io/design-system"

interface LogFilter {
    field: string
    operation: string
    value: string
}

interface SearchLogsParams {
    size?: number
    filters?: LogFilter[]
}

const searchLogs = vi.fn()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs: (...args: unknown[]) => searchLogs(...args),
    deleteLogsFromFlow: vi.fn(),
}))

import LogsWrapper from "./LogsWrapper.vue"
import QuickFilters from "../filter/QuickFilters.vue"

const HOUR = 60 * 60 * 1000

// What the server holds: two lines from three days ago and one from five minutes ago.
const storedLogs = () => [
    {message: "three days ago", timestamp: new Date(Date.now() - 72 * HOUR).toISOString()},
    {message: "three days ago too", timestamp: new Date(Date.now() - 71 * HOUR).toISOString()},
    {message: "five minutes ago", timestamp: new Date(Date.now() - 5 * 60 * 1000).toISOString()},
].map((log) => ({level: "INFO", namespace: "ns", flowId: "flow", executionId: "execution", ...log}))

let serverLogs = storedLogs

// A minimal stand-in for the search endpoint: it applies the relative time range it is sent.
const searchLike = (params: SearchLogsParams) => {
    const timeRange = params.filters?.find((f) => f.field === "timeRange")?.value
    const since = timeRange ? Date.now() - dayjs.duration(timeRange).asMilliseconds() : 0
    const results = serverLogs()
        .filter((log) => Date.parse(log.timestamp) >= since)
        .sort((a, b) => b.timestamp.localeCompare(a.timestamp))
        .slice(0, params.size)
    return Promise.resolve({results, total: results.length, type: "OFFSET"})
}

const mounted: VueWrapper[] = []

const settle = async () => {
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 200))
    await flushPromises()
}

async function mountAtLogsList(query: Record<string, string>, props: Record<string, unknown> = {}) {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{name: "logs/list", path: "/:tenant?/logs", component: {template: "<div/>"}}],
    })
    await router.push({name: "logs/list", params: {tenant: "main"}, query})
    await router.isReady()
    const wrapper = i18nMount(LogsWrapper, {
        props,
        global: {
            plugins: [createPinia(), router, KestraDesignSystem],
            stubs: {
                Sections: true,
                TopNavBar: true,
                KsDialog: {
                    props: ["modelValue"],
                    template: "<div v-if=\"modelValue\" data-test=\"download-dialog\"><slot /><slot name=\"footer\" /></div>",
                },
            },
        },
    })
    mounted.push(wrapper)
    await settle()
    return wrapper
}

async function downloadWithInterval(wrapper: VueWrapper, interval?: string) {
    await wrapper.find("[aria-label=\"download logs\"]").trigger("click")
    await flushPromises()
    const dialog = wrapper.find("[data-test=\"download-dialog\"]")
    if (interval) {
        dialog.findComponent(QuickFilters).vm.$emit("update:timeRange", interval)
        await flushPromises()
    }
    searchLogs.mockClear()
    await dialog.findAll("button").find((button) => button.text() === "download")!.trigger("click")
    await settle()
}

const downloadRequestFilters = () =>
    searchLogs.mock.calls
        .map(([params]) => params as SearchLogsParams)
        .filter((params) => params.size === 1000)
        .map((params) => params.filters)

const timeFiltersOf = (filters: LogFilter[] | undefined) =>
    (filters ?? []).filter((f) => ["timeRange", "startDate", "endDate"].includes(f.field))

describe("LogsWrapper download dialog", () => {
    let downloadedFiles: Blob[]
    const originalUrl = {createObjectURL: URL.createObjectURL, revokeObjectURL: URL.revokeObjectURL}

    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        searchLogs.mockReset()
        serverLogs = storedLogs
        searchLogs.mockImplementation(searchLike)
        downloadedFiles = []
        Object.assign(URL, {
            createObjectURL: (blob: Blob) => {
                downloadedFiles.push(blob)
                return "blob:logs"
            },
            revokeObjectURL: vi.fn(),
        })
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
    })

    afterEach(() => {
        mounted.splice(0).forEach((wrapper) => wrapper.unmount())
        Object.assign(URL, originalUrl)
        vi.restoreAllMocks()
        window.sessionStorage.clear()
        window.localStorage.clear()
        document.title = ""
    })

    it("downloads the 7d picked in the dialog, not the page's last 24 hours", async () => {
        const wrapper = await mountAtLogsList({"filters[timeRange][EQUALS]": "PT24H"})

        await downloadWithInterval(wrapper, "PT168H")

        const requests = downloadRequestFilters()
        expect(requests).toHaveLength(1)
        expect(timeFiltersOf(requests[0])).toEqual([{field: "timeRange", operation: "EQUALS", value: "PT168H"}])
        expect(downloadedFiles).toHaveLength(1)
        const text = await downloadedFiles[0].text()
        expect(text).toContain("three days ago")
        expect(text).toContain("five minutes ago")
    })

    it("downloads only the 15m picked in the dialog when the page shows 7 days", async () => {
        const wrapper = await mountAtLogsList({"filters[timeRange][EQUALS]": "PT168H"})

        await downloadWithInterval(wrapper, "PT15M")

        expect(timeFiltersOf(downloadRequestFilters()[0])).toEqual([{field: "timeRange", operation: "EQUALS", value: "PT15M"}])
        const text = await downloadedFiles[0].text()
        expect(text).toContain("five minutes ago")
        expect(text).not.toContain("three days ago")
    })

    it("still produces a file when every log is older than the page's range but inside the dialog's", async () => {
        serverLogs = () => storedLogs().filter((log) => log.message !== "five minutes ago")
        const wrapper = await mountAtLogsList({"filters[timeRange][EQUALS]": "PT1H"})

        await downloadWithInterval(wrapper, "PT168H")

        expect(downloadedFiles).toHaveLength(1)
        expect(await downloadedFiles[0].text()).toContain("three days ago")
    })

    it("replaces a brushed absolute range from the page with the dialog's range", async () => {
        const wrapper = await mountAtLogsList({
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": new Date(Date.now() - 2 * HOUR).toISOString(),
            "filters[endDate][LESS_THAN_OR_EQUAL_TO]": new Date(Date.now() - HOUR).toISOString(),
        })

        await downloadWithInterval(wrapper, "PT168H")

        expect(timeFiltersOf(downloadRequestFilters()[0])).toEqual([{field: "timeRange", operation: "EQUALS", value: "PT168H"}])
    })

    it("keeps an embedded wrapper's own window when the dialog is left alone", async () => {
        const startDate = new Date(Date.now() - 2 * HOUR).toISOString()
        const endDate = new Date(Date.now() - HOUR).toISOString()
        const wrapper = await mountAtLogsList({"filters[timeRange][EQUALS]": "PT168H"}, {
            embed: true,
            withCharts: false,
            filters: {
                "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": startDate,
                "filters[endDate][LESS_THAN_OR_EQUAL_TO]": endDate,
            },
        })

        await downloadWithInterval(wrapper)

        expect(timeFiltersOf(downloadRequestFilters()[0])).toEqual([
            {field: "startDate", operation: "GREATER_THAN_OR_EQUAL_TO", value: startDate},
            {field: "endDate", operation: "LESS_THAN_OR_EQUAL_TO", value: endDate},
        ])
    })
})
