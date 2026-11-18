import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import ExecutionLogs from "./Logs.vue"

const {route} = vi.hoisted(() => ({
    route: {query: {} as Record<string, string>, params: {}, name: "executions/update"},
}))

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: vi.fn().mockResolvedValue({data: {results: [], total: 0}}),
        post: vi.fn().mockResolvedValue({data: {}}),
    }),
}))

const globalConfig = {
    plugins: [KestraDesignSystem],
    stubs: {
        KSFilter: true,
        TaskRunDetails: {props: ["taskRunId"], template: "<div class=\"task-run-details\" :data-task-run-id=\"taskRunId\" />"},
        Restart: true,
        LogDisplaySettings: true,
        LogLine: true,
        DynamicScroller: true,
        DynamicScrollerItem: true,
    },
}

describe("executions/Logs task run filter", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        vi.restoreAllMocks()
        localStorage.clear()
        route.query = {}
    })

    test("restricts the grouped view to the task run named by the route filter", async () => {
        // Given
        route.query = {"filters[taskRunId][EQUALS]": "tr-1"}

        // When
        const wrapper = i18nMount(ExecutionLogs, {global: globalConfig})
        await flushPromises()

        // Then
        expect(wrapper.find(".task-run-details").attributes("data-task-run-id")).toBe("tr-1")
    })

    test("shows every task run without the route filter", async () => {
        // Given / When
        const wrapper = i18nMount(ExecutionLogs, {global: globalConfig})
        await flushPromises()

        // Then
        expect(wrapper.find(".task-run-details").attributes("data-task-run-id")).toBeUndefined()
    })
})
