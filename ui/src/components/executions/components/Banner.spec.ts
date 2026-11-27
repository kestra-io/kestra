import {beforeEach, describe, expect, test, vi} from "vitest"
import {reactive, type Plugin} from "vue"
import {setActivePinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import {routedPinia} from "../../../../tests/unit/memoryRouter"
import {flushPromises} from "@vue/test-utils"
import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
import Banner from "./Banner.vue"
import {useExecutionsStore, type Execution} from "../../../stores/executions"
import {useMiscStore} from "override/stores/misc"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import * as SearchableDataAPI from "@kestra-io/kestra-sdk/searchable-data"

vi.mock("@kestra-io/kestra-sdk/searchable-data", () => ({
    listSearchableData: vi.fn().mockResolvedValue({results: [], total: 0}),
}))

const triggerScope = reactive({visible: false, enabled: false})
const denied = vi.hoisted(() => new Set<string>())

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {
        isAllowed: (resource: string, act: string, namespace: string) =>
            namespace === "io.kestra.tests" && !denied.has(`${resource}.${act}`),
        hasAny: (resource: string) => !denied.has(resource),
    }}),
}))

const globalConfig = {
    stubs: {
        RouterLink: {props: ["to"], template: "<a><slot /></a>"},
        KsTooltip: {template: "<div><slot /></div>"},
        KsIconButton: {template: "<button><slot /></button>"},
        SetLabels: true,
        ApprovalActions: {props: ["execution", "taskRun"], template: "<span data-test=\"approval-action\">{{ taskRun.id }}</span>"},
        RunTimeline: true,
        Duration: true,
        ChangeExecutionStatus: {
            props: ["execution"],
            setup() {
                return {triggerScope}
            },
            template: "<div><slot name=\"trigger\" v-bind=\"triggerScope\" /></div>",
        },
    },
}

function buildExecution(current = "SUCCESS"): Execution {
    return {
        id: "execution-id",
        originalId: "execution-id",
        namespace: "io.kestra.tests",
        flowId: "flow",
        flowRevision: 1,
        labels: [],
        taskRunList: [],
        metadata: {
            originalCreatedDate: "2026-01-01T00:00:00Z",
            attemptNumber: 1,
        },
        state: {
            current,
            histories: [],
            getStartDate: "2026-01-01T00:00:00Z",
            getEndDate: "",
            getDuration: "PT1S",
        },
    } as Execution
}

function mountBanner(execution = buildExecution(), plugins: Plugin[] = []) {
    return i18nMount(Banner, {
        messages: {execution_banner: {more_searchable_values: "+{count} more searchable values"}},
        props: {execution},
        global: {...globalConfig, plugins},
    })
}

describe("Banner", () => {
    beforeEach(() => {
        vi.mocked(SearchableDataAPI.listSearchableData).mockResolvedValue({results: [], total: 0})
        setActivePinia(routedPinia())
        triggerScope.visible = false
        triggerScope.enabled = false
        denied.clear()
    })

    test("offers to copy the logs only to a user who can access them", () => {
        expect(mountBanner().text()).toContain("copy logs")

        denied.add("EXECUTION.ACCESS_LOGS")

        expect(mountBanner().text()).not.toContain("copy logs")
    })

    test("leaves copying the logs to the logs toolbar on the Logs tab", async () => {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{path: "/:pathMatch(.*)*", component: {render: () => null}, meta: {tab: "logs"}}],
        })
        await router.push("/logs")

        expect(mountBanner(buildExecution(), [router]).text()).not.toContain("copy logs")
    })

    test("hides the labels that repeat the execution id and the user shown above them", () => {
        const labelled = (correlationId: string) => ({
            ...buildExecution(),
            labels: [
                {key: "system.from", value: "api"},
                {key: "system.username", value: "someone@yourcompany.com"},
                {key: "system.correlationId", value: correlationId},
            ],
        }) as Execution

        const own = mountBanner(labelled("execution-id")).text()
        expect(own).toContain("system.from: api")
        expect(own).not.toContain("system.correlationId")
        expect(own.match(/someone@yourcompany\.com/g)).toHaveLength(1)

        expect(mountBanner(labelled("parent-id")).text()).toContain("system.correlationId: parent-id")
    })

    test("renders no chevron when the state change trigger is disabled", () => {
        triggerScope.enabled = false
        const wrapper = mountBanner()

        expect(wrapper.findComponent(ChevronDown).exists()).toBe(false)
        expect(wrapper.findComponent(ChevronUp).exists()).toBe(false)
    })

    test("renders the down chevron when enabled and the popover is closed", () => {
        triggerScope.enabled = true
        triggerScope.visible = false
        const wrapper = mountBanner()

        expect(wrapper.findComponent(ChevronDown).exists()).toBe(true)
        expect(wrapper.findComponent(ChevronUp).exists()).toBe(false)
    })

    test("renders the up chevron when enabled and the popover is open", () => {
        triggerScope.enabled = true
        triggerScope.visible = true
        const wrapper = mountBanner()

        expect(wrapper.findComponent(ChevronUp).exists()).toBe(true)
        expect(wrapper.findComponent(ChevronDown).exists()).toBe(false)
    })

    test("offers to fix with AI only to a user who can use the copilot", () => {
        expect(mountBanner(buildExecution("FAILED")).find(".fix-with-ai").exists()).toBe(true)

        denied.add("COPILOT")

        expect(mountBanner(buildExecution("FAILED")).find(".fix-with-ai").exists()).toBe(false)
    })

    test("links the flow id to the flow only for a user who can view it", () => {
        expect(mountBanner().find(".execution-banner__flow").element.tagName).toBe("A")

        denied.add("FLOW.VIEW")

        const flow = mountBanner().find(".execution-banner__flow")
        expect(flow.element.tagName).toBe("SPAN")
        expect(flow.text()).toBe(buildExecution().flowId)
    })

    test("fix with AI asks only for the first page of ERROR logs", async () => {
        const loadLogs = vi.spyOn(useExecutionsStore(), "loadLogs").mockResolvedValue([
            {message: "Ledger EMEA is off"},
            {message: "Ledger AMER is off"},
        ] as never)
        const promptCopilot = vi.spyOn(useMiscStore(), "promptCopilot").mockImplementation(() => undefined)
        const wrapper = mountBanner(buildExecution("FAILED"))

        await wrapper.find(".fix-with-ai").trigger("click")
        await flushPromises()

        expect(loadLogs).toHaveBeenCalledWith({
            store: false,
            executionId: "execution-id",
            filters: [{field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "ERROR"}],
            page: 1,
            size: 100,
        })

        expect(promptCopilot).toHaveBeenCalledWith(
            "Fix the flow flow as it generated the following error:\nLedger EMEA is off\nLedger AMER is off",
            expect.anything(),
        )
    })

    test("lists the first searchable outputs under the labels", async () => {
        vi.mocked(SearchableDataAPI.listSearchableData).mockResolvedValue({
            results: [{dataId: "customerId", value: "CUST-12"}, {dataId: "orderIds", value: "ORD-7000"}],
            total: 2,
        })
        const wrapper = mountBanner()
        await flushPromises()

        expect(SearchableDataAPI.listSearchableData).toHaveBeenCalledWith({executionId: "execution-id", page: 1, size: 10})
        expect(wrapper.find("[data-test=searchable-outputs]").text()).toContain("customerId: CUST-12")
        expect(wrapper.find("[data-test=searchable-outputs]").text()).toContain("orderIds: ORD-7000")
        expect(wrapper.find("[data-test=more-searchable-outputs]").exists()).toBe(false)
    })

    test("tells how many searchable outputs are not displayed", async () => {
        vi.mocked(SearchableDataAPI.listSearchableData).mockResolvedValue({
            results: [{dataId: "orderIds", value: "ORD-7000"}],
            total: 1234,
        })
        const wrapper = mountBanner()
        await flushPromises()

        expect(wrapper.find("[data-test=more-searchable-outputs]").text()).toBe("+1,233 more searchable values")
    })

    test("clears the searchable outputs of the previous execution while the next one loads", async () => {
        vi.mocked(SearchableDataAPI.listSearchableData).mockResolvedValueOnce({results: [{dataId: "orderIds", value: "first"}], total: 1})
        const execution = reactive(buildExecution())
        const wrapper = i18nMount(Banner, {props: {execution}, global: globalConfig})
        await flushPromises()
        vi.mocked(SearchableDataAPI.listSearchableData).mockReturnValueOnce(new Promise(() => undefined))

        execution.id = "another-execution"
        await flushPromises()

        expect(wrapper.find("[data-test=searchable-outputs]").exists()).toBe(false)
    })

    test("shows no searchable outputs row when the execution has none or the lookup fails", async () => {
        const empty = mountBanner()
        await flushPromises()

        vi.mocked(SearchableDataAPI.listSearchableData).mockRejectedValue({status: 404})
        const failing = mountBanner()
        await flushPromises()

        expect(empty.find("[data-test=searchable-outputs]").exists()).toBe(false)
        expect(failing.find("[data-test=searchable-outputs]").exists()).toBe(false)
    })

    test("ignores a searchable outputs response that a newer load has superseded", async () => {
        let resolveFirst: (value: {results: {dataId: string; value: string}[]; total: number}) => void = () => undefined
        vi.mocked(SearchableDataAPI.listSearchableData)
            .mockImplementationOnce(() => new Promise((resolve) => {
                resolveFirst = resolve
            }))
            .mockResolvedValueOnce({results: [{dataId: "orderIds", value: "fresh"}], total: 1})
        const execution = reactive(buildExecution("RUNNING"))
        const wrapper = i18nMount(Banner, {props: {execution}, global: globalConfig})

        execution.state.current = "SUCCESS"
        await flushPromises()
        resolveFirst({results: [{dataId: "orderIds", value: "stale"}], total: 1})
        await flushPromises()

        expect(wrapper.find("[data-test=searchable-outputs]").text()).toContain("orderIds: fresh")
        expect(wrapper.find("[data-test=searchable-outputs]").text()).not.toContain("stale")
    })

    test("explains a pausing execution that will ask for an approval", () => {
        const banner = mountBanner(buildExecution("PAUSING")).find("[data-test='approval-banner']")

        expect(banner.text()).toContain("approval.banner.pausing")
    })

    test("asks for a review only when the paused task run is an approval", () => {
        useExecutionsStore().flow = {tasks: [
            {id: "approval", type: "io.kestra.plugin.core.flow.Approval"},
            {id: "pause", type: "io.kestra.plugin.core.flow.Pause"},
        ]} as never
        const pausedOn = (taskId: string) => ({
            ...buildExecution("PAUSED"),
            taskRunList: [{id: "tr", taskId, state: {current: "PAUSED"}}],
        }) as unknown as Execution

        expect(mountBanner(pausedOn("approval")).find("[data-test='approval-banner']").text()).toContain("approval.banner.paused")
        expect(mountBanner(pausedOn("pause")).find("[data-test='approval-banner']").exists()).toBe(false)
    })

    test("hands the approval task run to the review actions, while it pauses as once it is paused", () => {
        useExecutionsStore().flow = {tasks: [{id: "approval", type: "io.kestra.plugin.core.flow.Approval"}]} as never
        const approvalIn = (state: string) => ({
            ...buildExecution(state),
            taskRunList: [{id: `tr-${state}`, taskId: "approval", state: {current: state}}],
        }) as unknown as Execution

        expect(mountBanner(approvalIn("PAUSING")).find("[data-test='approval-action']").text()).toBe("tr-PAUSING")
        expect(mountBanner(approvalIn("PAUSED")).find("[data-test='approval-action']").text()).toBe("tr-PAUSED")
    })
})
