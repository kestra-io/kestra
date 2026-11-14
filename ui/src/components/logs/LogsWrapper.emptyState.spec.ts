import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

import {createPinia} from "pinia"
import {createMemoryHistory, createRouter, type Router} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"
import type {searchLogs as SdkSearchLogs} from "@kestra-io/kestra-sdk/logs"

const searchLogs = vi.fn<typeof SdkSearchLogs>()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs: (...args: Parameters<typeof SdkSearchLogs>) => searchLogs(...args),
    deleteLogsFromFlow: vi.fn(),
}))

import LogsWrapper from "./LogsWrapper.vue"

const messages = {
    no_logs_data_title: "No logs match",
    no_logs_in_range_title: "No logs in range",
    no_logs_in_range_at_level_title: "No {level} logs in range",
}

const settle = (wrapper: VueWrapper) => vi.waitFor(() => {
    expect(searchLogs).toHaveBeenCalled()
    expect(wrapper.text()).toContain("No ")
}, {timeout: 5000})

async function mountOn(query: Record<string, string>) {
    const router: Router = createRouter({
        history: createMemoryHistory(),
        routes: [{name: "logs/list", path: "/:tenant?/logs", component: {template: "<div/>"}}],
    })
    await router.push({name: "logs/list", params: {tenant: "main"}, query})
    await router.isReady()
    const wrapper = i18nMount(LogsWrapper, {
        messages,
        global: {
            plugins: [createPinia(), router, KestraDesignSystem],
            stubs: {Sections: true, TopNavBar: true},
        },
    })
    await settle(wrapper)
    return {wrapper, router}
}

describe("LogsWrapper empty state", () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        searchLogs.mockReset()
        searchLogs.mockResolvedValue({results: [], total: 0, type: "OFFSET"})
    })

    afterEach(() => {
        window.sessionStorage.clear()
        window.localStorage.clear()
        document.title = ""
    })

    it("names the default level on a bare page once the filter has written its defaults", async () => {
        const {wrapper, router} = await mountOn({})

        expect(router.currentRoute.value.query["filters[timeRange][EQUALS]"]).toBe("PT24H")
        expect(wrapper.text()).toContain("No INFO logs in range")
        expect(wrapper.text()).not.toContain(messages.no_logs_data_title)
    })

    it("names the default level when the configured default namespace is applied", async () => {
        window.localStorage.setItem("defaultNamespace", "company")

        const {wrapper, router} = await mountOn({})

        expect(router.currentRoute.value.query["filters[namespace][PREFIX]"]).toBe("company")
        expect(wrapper.text()).toContain("No INFO logs in range")
    })

    it("names no level when the default level hides nothing", async () => {
        window.localStorage.setItem("defaultLogLevel", "TRACE")

        const {wrapper} = await mountOn({})

        expect(wrapper.text()).toContain(messages.no_logs_in_range_title)
    })

    it("shows the no match message when the user narrowed the time range", async () => {
        const {wrapper} = await mountOn({"filters[timeRange][EQUALS]": "PT1H"})

        expect(wrapper.text()).toContain(messages.no_logs_data_title)
        expect(wrapper.text()).not.toContain("in range")
    })
})
