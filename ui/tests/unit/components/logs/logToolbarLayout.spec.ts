import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"

import LogsWrapper from "../../../../src/components/logs/LogsWrapper.vue"
import ExecutionLogs from "../../../../src/components/executions/Logs.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "logs"}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: vi.fn().mockResolvedValue({data: {results: [], total: 0}}),
        post: vi.fn().mockResolvedValue({data: {}}),
    }),
}))

const globalConfig = {
    plugins: [
        createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false}),
        KestraDesignSystem,
    ],
    stubs: {
        TopNavBar: true,
        KSFilter: true,
        Sections: true,
        LogLine: true,
        LogDisplaySettings: true,
        KsDialog: true,
        KsDataTable: {
            template: "<div><slot name=\"table\" /></div>",
        },
    },
}

const executionLogsGlobalConfig = {
    plugins: [
        createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false}),
        KestraDesignSystem,
    ],
    stubs: {
        KSFilter: true,
        TaskRunDetails: true,
        Restart: true,
        LogDisplaySettings: true,
        LogLine: true,
        DynamicScroller: true,
        DynamicScrollerItem: true,
    },
}

describe("LogsWrapper toolbar layout", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    test("quick-filters level legend is absent and the logs toolbar still renders", async () => {
        // Given
        const wrapper = mount(LogsWrapper, {global: globalConfig})
        await flushPromises()

        // Then
        expect(wrapper.find("[data-test='quick-filters-level']").exists()).toBe(false)
        expect(wrapper.find(".logs-toolbar").exists()).toBe(true)
    })
})

describe("executions/Logs toolbar layout", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    test("QuickFilters level legend is present", async () => {
        // Given
        const wrapper = mount(ExecutionLogs, {global: executionLogsGlobalConfig})
        await flushPromises()

        // Then
        expect(wrapper.find("[data-test='quick-filters-level']").exists()).toBe(true)
    })

    test("toolbar actions contain Download and Copy buttons but no Refresh button", async () => {
        // Given
        const wrapper = mount(ExecutionLogs, {global: executionLogsGlobalConfig})
        await flushPromises()

        // Then
        const actions = wrapper.find(".logs-toolbar__actions")
        expect(actions.exists()).toBe(true)
        expect(actions.find("[aria-label='download logs']").exists()).toBe(true)
        expect(actions.find("[aria-label='copy logs']").exists()).toBe(true)
        expect(actions.find("[aria-label='refresh']").exists()).toBe(false)
        expect(actions.find("[aria-label='Refresh']").exists()).toBe(false)
    })
})
