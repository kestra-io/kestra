import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
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
    no_logs_yet_title: "No logs yet",
}

const settle = async () => {
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 200))
    await flushPromises()
}

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
    await settle()
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

    it("shows the nothing yet message on a bare page once the filter has written its defaults", async () => {
        const {wrapper, router} = await mountOn({})

        expect(router.currentRoute.value.query["filters[timeRange][EQUALS]"]).toBe("PT24H")
        expect(wrapper.text()).toContain(messages.no_logs_yet_title)
        expect(wrapper.text()).not.toContain(messages.no_logs_data_title)
    })

    it("shows the nothing yet message when the configured default namespace is applied", async () => {
        window.localStorage.setItem("defaultNamespace", "company")

        const {wrapper, router} = await mountOn({})

        expect(router.currentRoute.value.query["filters[namespace][PREFIX]"]).toBe("company")
        expect(wrapper.text()).toContain(messages.no_logs_yet_title)
    })

    it("shows the no match message when the user narrowed the time range", async () => {
        const {wrapper} = await mountOn({"filters[timeRange][EQUALS]": "PT1H"})

        expect(wrapper.text()).toContain(messages.no_logs_data_title)
        expect(wrapper.text()).not.toContain(messages.no_logs_yet_title)
    })
})
