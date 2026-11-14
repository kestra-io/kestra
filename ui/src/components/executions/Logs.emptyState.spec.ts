import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import ExecutionLogs from "./Logs.vue"
import {useExecutionsStore} from "../../stores/executions"
import {storageKeys} from "../../utils/constants"

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

const messages = {
    no_logs_data_title: "No logs match",
    execution_no_logs_title: "No logs in this execution",
    execution_no_logs_at_level_title: "No {level} logs in this execution",
}

function mountEmptyLogs() {
    const store = useExecutionsStore()
    store.execution = {id: "root", namespace: "ns", flowId: "hello", state: {current: "SUCCESS"}} as never
    store.flow = {id: "hello", namespace: "ns", tasks: []} as never
    store.loadLogs = vi.fn().mockResolvedValue(undefined)
    return i18nMount(ExecutionLogs, {
        messages,
        global: {
            plugins: [KestraDesignSystem],
            stubs: {KSFilter: true, Restart: true, LogDisplaySettings: true, DynamicScroller: true, DynamicScrollerItem: true},
        },
    })
}

describe("executions/Logs empty state", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        localStorage.setItem(storageKeys.LOGS_VIEW_TYPE, "true")
    })

    afterEach(() => {
        vi.restoreAllMocks()
        localStorage.clear()
        route.query = {}
    })

    test("shouldNameTheDefaultLevelWhenNoFilterIsApplied", async () => {
        const wrapper = mountEmptyLogs()
        await flushPromises()

        expect(wrapper.text()).toContain("No INFO logs in this execution")
        expect(wrapper.text()).not.toContain(messages.no_logs_data_title)
    })

    test("shouldSayTheExecutionHasNoLogsWhenNoLevelIsHidden", async () => {
        localStorage.setItem(storageKeys.DEFAULT_LOG_LEVEL, "TRACE")

        const wrapper = mountEmptyLogs()
        await flushPromises()

        expect(wrapper.text()).toContain(messages.execution_no_logs_title)
        expect(wrapper.text()).not.toContain(messages.no_logs_data_title)
    })

    test("shouldShowTheNoMatchMessageWhenTheLevelIsNarrowed", async () => {
        route.query = {"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"}

        const wrapper = mountEmptyLogs()
        await flushPromises()

        expect(wrapper.text()).toContain(messages.no_logs_data_title)
        expect(wrapper.text()).not.toContain("in this execution")
    })
})
