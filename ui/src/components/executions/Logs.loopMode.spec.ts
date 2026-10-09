import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {defineComponent} from "vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import ExecutionLogs from "./Logs.vue"
import {useExecutionsStore} from "../../stores/executions"

const LEVEL_KEY = "filters[level][GREATER_THAN_OR_EQUAL_TO]"

const routeState = vi.hoisted(() => ({route: undefined as unknown as {query: Record<string, string>; params: object; name: string}}))

vi.mock("vue-router", async () => {
    const {reactive} = await import("vue")
    routeState.route = reactive({query: {}, params: {}, name: "executions/update/logs"})
    return {
        useRoute: () => routeState.route,
        useRouter: () => ({
            push: vi.fn(),
            replace: vi.fn(async ({query}: {query: Record<string, string>}) => {
                routeState.route.query = query
            }),
        }),
    }
})

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: vi.fn().mockResolvedValue({data: {results: [], total: 0}}),
        post: vi.fn().mockResolvedValue({data: {}}),
    }),
}))

const MergedStub = defineComponent({
    props: {levelParams: {type: Object, default: () => ({})}},
    template: "<div data-test='loop-merged-logs-stub' />",
})

const FilterStub = defineComponent({
    emits: ["filter", "search"],
    template: "<div data-test='ks-filter' />",
})

const globalConfig = {
    plugins: [KestraDesignSystem],
    stubs: {
        KsFilter: FilterStub,
        TaskRunDetails: {template: "<div data-test='task-run-details' />"},
        LoopMergedLogs: MergedStub,
        Restart: true,
        LogDisplaySettings: true,
        LogLine: true,
        DynamicScroller: true,
        DynamicScrollerItem: true,
    },
}

const loopFlow = {id: "close_lite", namespace: "company.team", tasks: [{id: "per_customer", type: "io.kestra.plugin.core.flow.Loop"}]}
const plainFlow = {id: "hello", namespace: "company.team", tasks: [{id: "log", type: "io.kestra.plugin.core.log.Log"}]}

function mountLogs(flow: {id: string}, props: {playground?: boolean} = {}) {
    const store = useExecutionsStore()
    store.execution = {id: "root", namespace: "company.team", flowId: flow.id, state: {current: "SUCCESS"}} as never
    store.flow = flow as never
    return i18nMount(ExecutionLogs, {props, global: globalConfig})
}

const mergedLevel = (wrapper: ReturnType<typeof mountLogs>) =>
    wrapper.findComponent(MergedStub).props("levelParams") as Record<string, string>

const pickLevel = async (wrapper: ReturnType<typeof mountLogs>, value: string) => {
    wrapper.findComponent<typeof FilterStub>("[data-test='ks-filter']").vm.$emit("filter", [{key: "level", comparator: "GREATER_THAN_OR_EQUAL_TO", value}])
    await flushPromises()
}

describe("executions/Logs loop iterations mode", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        routeState.route.query = {}
    })

    afterEach(() => {
        vi.restoreAllMocks()
        localStorage.clear()
    })

    test("shouldDefaultToMergedLogsWithErrorsOnlyWhenTheFlowHasALoop", async () => {
        const wrapper = mountLogs(loopFlow)
        await flushPromises()

        expect(wrapper.find("[data-test='logs-mode-switch']").exists()).toBe(true)
        expect(wrapper.find("[data-test='loop-merged-logs-stub']").exists()).toBe(true)
        expect(wrapper.find("[data-test='task-run-details']").exists()).toBe(false)
        expect(routeState.route.query[LEVEL_KEY]).toBe("ERROR")
        expect(mergedLevel(wrapper)).toEqual({[LEVEL_KEY]: "ERROR"})
    })

    test("shouldKeepTheExistingViewAndDefaultLevelWhenTheFlowHasNoLoop", async () => {
        const wrapper = mountLogs(plainFlow)
        await flushPromises()

        expect(wrapper.find("[data-test='logs-mode-switch']").exists()).toBe(false)
        expect(wrapper.find("[data-test='task-run-details']").exists()).toBe(true)
        expect(routeState.route.query[LEVEL_KEY]).toBe("INFO")
    })

    test("shouldNotOfferMergedModeInThePlayground", async () => {
        const wrapper = mountLogs(loopFlow, {playground: true})
        await flushPromises()

        expect(wrapper.find("[data-test='logs-mode-switch']").exists()).toBe(false)
        expect(wrapper.find("[data-test='loop-merged-logs-stub']").exists()).toBe(false)
    })

    test("shouldPreserveALevelThatIsAlreadyInTheUrl", async () => {
        routeState.route.query = {[LEVEL_KEY]: "WARN"}
        const wrapper = mountLogs(loopFlow)
        await flushPromises()

        expect(routeState.route.query[LEVEL_KEY]).toBe("WARN")
        expect(mergedLevel(wrapper)).toEqual({[LEVEL_KEY]: "WARN"})
    })

    test("shouldPreserveTheLevelTheUserPicksInMergedMode", async () => {
        const wrapper = mountLogs(loopFlow)
        await flushPromises()

        await pickLevel(wrapper, "WARN")

        expect(routeState.route.query[LEVEL_KEY]).toBe("WARN")
        expect(mergedLevel(wrapper)).toEqual({[LEVEL_KEY]: "WARN"})
    })

    test("shouldRestoreThePreviousLevelWhenLeavingMergedModeWithoutAPick", async () => {
        const wrapper = mountLogs(loopFlow)
        await flushPromises()
        expect(routeState.route.query[LEVEL_KEY]).toBe("ERROR")

        await wrapper.findAll("[data-test='logs-mode-switch'] input")[0].setValue(true)
        await flushPromises()

        expect(wrapper.find("[data-test='task-run-details']").exists()).toBe(true)
        expect(routeState.route.query[LEVEL_KEY]).toBe("INFO")
    })

    test("shouldKeepThePickedLevelWhenLeavingMergedMode", async () => {
        const wrapper = mountLogs(loopFlow)
        await flushPromises()
        await pickLevel(wrapper, "WARN")

        await wrapper.findAll("[data-test='logs-mode-switch'] input")[0].setValue(true)
        await flushPromises()

        expect(routeState.route.query[LEVEL_KEY]).toBe("WARN")
    })

    test("shouldApplyErrorsOnlyWhenTheFlowArrivesAfterTheTabMounted", async () => {
        const store = useExecutionsStore()
        store.execution = {id: "root", namespace: "company.team", flowId: "close_lite", state: {current: "SUCCESS"}} as never
        const wrapper = i18nMount(ExecutionLogs, {global: globalConfig})
        await flushPromises()
        expect(routeState.route.query[LEVEL_KEY]).toBe("INFO")

        store.flow = loopFlow as never
        await flushPromises()

        expect(routeState.route.query[LEVEL_KEY]).toBe("ERROR")
        expect(wrapper.find("[data-test='loop-merged-logs-stub']").exists()).toBe(true)
    })
})
