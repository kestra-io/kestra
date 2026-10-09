import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import ExecutionLogs from "./Logs.vue"
import {useExecutionsStore} from "../../stores/executions"

const replace = vi.fn()

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "executions/update/logs"}),
    useRouter: () => ({push: vi.fn(), replace}),
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
        TaskRunDetails: {template: "<div data-test='task-run-details' />"},
        LoopMergedLogs: {template: "<div data-test='loop-merged-logs-stub' />"},
        Restart: true,
        LogDisplaySettings: true,
        LogLine: true,
        DynamicScroller: true,
        DynamicScrollerItem: true,
    },
}

const loopFlow = {id: "close_lite", namespace: "company.team", tasks: [{id: "per_customer", type: "io.kestra.plugin.core.flow.Loop"}]}
const plainFlow = {id: "hello", namespace: "company.team", tasks: [{id: "log", type: "io.kestra.plugin.core.log.Log"}]}

function mountLogs(flow: object) {
    const store = useExecutionsStore()
    store.execution = {id: "root", namespace: "company.team", flowId: flow["id" as keyof typeof flow], state: {current: "SUCCESS"}} as never
    store.flow = flow as never
    return i18nMount(ExecutionLogs, {global: globalConfig})
}

describe("executions/Logs loop iterations mode", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        replace.mockClear()
    })

    afterEach(() => {
        vi.restoreAllMocks()
        localStorage.clear()
    })

    test("shouldDefaultToMergedLogsWithErrorsOnlyWhenTheFlowHasALoop", async () => {
        // Given
        const wrapper = mountLogs(loopFlow)
        await flushPromises()

        // Then
        expect(wrapper.find("[data-test='logs-mode-switch']").exists()).toBe(true)
        expect(wrapper.find("[data-test='loop-merged-logs-stub']").exists()).toBe(true)
        expect(wrapper.find("[data-test='task-run-details']").exists()).toBe(false)
        expect(JSON.stringify(replace.mock.calls)).toContain("ERROR")
    })

    test("shouldKeepTheExistingViewAndDefaultLevelWhenTheFlowHasNoLoop", async () => {
        // Given
        const wrapper = mountLogs(plainFlow)
        await flushPromises()

        // Then
        expect(wrapper.find("[data-test='logs-mode-switch']").exists()).toBe(false)
        expect(wrapper.find("[data-test='loop-merged-logs-stub']").exists()).toBe(false)
        expect(wrapper.find("[data-test='task-run-details']").exists()).toBe(true)
        expect(JSON.stringify(replace.mock.calls)).not.toContain("ERROR")
    })

    test("shouldShowTheExecutionViewWhenTheUserSwitchesBackFromMerged", async () => {
        // Given
        const wrapper = mountLogs(loopFlow)
        await flushPromises()

        // When
        await wrapper.findAll("[data-test='logs-mode-switch'] input")[0].setValue(true)
        await flushPromises()

        // Then
        expect(wrapper.find("[data-test='loop-merged-logs-stub']").exists()).toBe(false)
        expect(wrapper.find("[data-test='task-run-details']").exists()).toBe(true)
    })
})
