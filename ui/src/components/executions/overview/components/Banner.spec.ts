import {beforeEach, describe, expect, test, vi} from "vitest"
import {reactive} from "vue"
import {createPinia, setActivePinia} from "pinia"
import {flushPromises} from "@vue/test-utils"
import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
import Banner from "./Banner.vue"
import {useExecutionsStore, type Execution} from "../../../../stores/executions"
import {useMiscStore} from "override/stores/misc"
import {routeQueryToQueryFilters} from "../../../../utils/queryFilters"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

const triggerScope = reactive({visible: false, enabled: false})

const globalConfig = {
    stubs: {
        RouterLink: {props: ["to"], template: "<a><slot /></a>"},
        KsTooltip: {template: "<div><slot /></div>"},
        KsIconButton: {template: "<button><slot /></button>"},
        SetLabels: true,
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

function mountBanner(execution = buildExecution()) {
    return i18nMount(Banner, {
        props: {execution},
        global: globalConfig,
    })
}

describe("Banner", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        triggerScope.visible = false
        triggerScope.enabled = false
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
            params: {"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"},
            page: 1,
            size: 100,
        })
        expect(routeQueryToQueryFilters(loadLogs.mock.calls[0][0].params ?? {})).toEqual([
            {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "ERROR"},
        ])
        expect(promptCopilot).toHaveBeenCalledWith(
            "Fix the flow flow as it generated the following error:\nLedger EMEA is off\nLedger AMER is off",
            expect.anything(),
        )
    })
})
