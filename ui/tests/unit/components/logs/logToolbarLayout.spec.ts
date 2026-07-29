import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"

import ExecutionLogs from "../../../../src/components/executions/Logs.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "executions/update"}),
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
        KSFilter: true,
        TaskRunDetails: true,
        Restart: true,
        LogDisplaySettings: true,
        LogLine: true,
        DynamicScroller: true,
        DynamicScrollerItem: true,
    },
}

describe("executions/Logs toolbar layout", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    test("toolbar actions keep Download and Copy but no longer render a standalone Refresh", async () => {
        // Given
        const wrapper = mount(ExecutionLogs, {global: globalConfig})
        await flushPromises()

        // When
        const actions = wrapper.find(".logs-toolbar__actions")

        // Then
        expect(actions.exists()).toBe(true)
        expect(actions.find("[aria-label='download logs']").exists()).toBe(true)
        expect(actions.find("[aria-label='copy logs']").exists()).toBe(true)
        expect(actions.find("[aria-label='refresh']").exists()).toBe(false)
    })
})
