import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {defineComponent, reactive, ref} from "vue"

const {executionsStore, routerState, loadTaskRunOutputsMock} = vi.hoisted(() => ({
    executionsStore: {execution: null as unknown},
    routerState: {
        route: {params: {}, query: {}} as {params: Record<string, string>, query: Record<string, unknown>},
        replace: vi.fn(),
    },
    loadTaskRunOutputsMock: vi.fn(() => Promise.resolve({})),
}))

vi.mock("vue-router", () => ({
    useRoute: () => routerState.route,
    useRouter: () => ({replace: routerState.replace}),
}))

vi.mock("../../stores/executions", () => ({useExecutionsStore: () => executionsStore}))
vi.mock("../../stores/plugins", () => ({usePluginsStore: () => ({fetchIcons: vi.fn()})}))
vi.mock("../../composables/useTaskRunOutputs", () => ({
    loadTaskRunOutputs: loadTaskRunOutputsMock,
    callTaskRunEndpoint: vi.fn(() => Promise.resolve({})),
}))
vi.mock("../plugins/TaskIcon.vue", () => ({default: {template: "<i />"}}))

import Artifacts from "./Artifacts.vue"
import {EXECUTION_ARTIFACTS_KEY} from "./composables/useExecutionArtifacts"
import {i18nMount} from "../../../tests/unit/i18nMount"

const deferred = <T,>() => {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((r) => { resolve = r })
    return {promise, resolve}
}

const RemoteStub = defineComponent({
    name: "RemoteComponentStub",
    props: ["fetchOutputs", "fetchMetrics", "fetchEndpoint"],
    template: "<div data-test=\"remote-component\" />",
})

describe("Artifacts", () => {
    beforeEach(() => {
        routerState.route = reactive({params: {}, query: {}})
        routerState.replace = vi.fn((target: {query?: Record<string, unknown>}) => {
            if (target.query) {
                routerState.route.query = {...target.query}
            }
        })
        loadTaskRunOutputsMock.mockClear()
    })

    it("shouldKeepLatestSubtitleWhenStaleResponseResolvesLast", async () => {
        // Given
        const calls = [deferred<string>(), deferred<string>()]
        const resolver = vi.fn().mockReturnValueOnce(calls[0].promise).mockReturnValueOnce(calls[1].promise)
        const execution = reactive({id: "e", namespace: "ns", flowId: "f", taskRunList: [{id: "r1", taskId: "dbt", state: {current: "RUNNING"}}]})
        executionsStore.execution = execution

        const wrapper = i18nMount(Artifacts, {
            global: {
                provide: {
                    [EXECUTION_ARTIFACTS_KEY as symbol]: {
                        RemoteComponent: ref(undefined),
                        artifacts: ref([{key: "dbt:graph", taskId: "dbt", taskType: "graph", cls: "dbt.Cli", title: "Model graph"}]),
                        tasks: ref([{id: "dbt", type: "dbt.Cli"}]),
                        subtitleResolvers: reactive({graph: resolver}),
                        manifestReady: ref(true),
                        hasResolvedComponent: () => false,
                        hasFailedComponent: () => false,
                    },
                },
            },
        })

        // When
        execution.taskRunList[0].state.current = "SUCCESS"
        await flushPromises()
        calls[1].resolve("8 nodes")
        await flushPromises()
        calls[0].resolve("")
        await flushPromises()

        // Then
        expect(wrapper.get("[data-test='execution-artifact-dbt:graph']").text()).toContain("8 nodes")
    })

    it("shouldDefaultToLatestIterationWhenQueryParameterIsOmitted", async () => {
        // Given
        const execution = reactive({
            id: "exec-1",
            namespace: "ns",
            flowId: "flow",
            taskRunList: [
                {id: "run-1", taskId: "loop-task", value: "item-1"},
                {id: "run-2", taskId: "loop-task", value: "item-2"},
            ],
        })
        executionsStore.execution = execution

        const wrapper = i18nMount(Artifacts, {
            global: {
                provide: {
                    [EXECUTION_ARTIFACTS_KEY as symbol]: {
                        RemoteComponent: ref(RemoteStub),
                        artifacts: ref([{key: "loop-task:graph", taskId: "loop-task", taskType: "graph", cls: "kestra.Loop", title: "Graph"}]),
                        tasks: ref([{id: "loop-task", type: "kestra.Loop"}]),
                        subtitleResolvers: reactive({}),
                        manifestReady: ref(true),
                        hasResolvedComponent: () => true,
                        hasFailedComponent: () => false,
                    },
                },
            },
        })
        await flushPromises()

        // Then: iteration select is shown and defaulted to latest run (run-2)
        const select = wrapper.findComponent({name: "KsSelect"})
        expect(select.exists()).toBe(true)
        expect(select.props("modelValue")).toBe("run-2")

        // And: RemoteComponent fetchOutputs defaults to run-2
        const remote = wrapper.findComponent(RemoteStub)
        expect(remote.exists()).toBe(true)
        remote.props("fetchOutputs")()
        expect(loadTaskRunOutputsMock).toHaveBeenCalledWith("exec-1", "run-2")
    })

    it("shouldRestoreSelectedIterationFromRouteQuery", async () => {
        // Given: iteration query specifies run-1
        routerState.route = reactive({params: {}, query: {iteration: "run-1"}})
        const execution = reactive({
            id: "exec-1",
            namespace: "ns",
            flowId: "flow",
            taskRunList: [
                {id: "run-1", taskId: "loop-task", value: "item-1"},
                {id: "run-2", taskId: "loop-task", value: "item-2"},
            ],
        })
        executionsStore.execution = execution

        const wrapper = i18nMount(Artifacts, {
            global: {
                provide: {
                    [EXECUTION_ARTIFACTS_KEY as symbol]: {
                        RemoteComponent: ref(RemoteStub),
                        artifacts: ref([{key: "loop-task:graph", taskId: "loop-task", taskType: "graph", cls: "kestra.Loop", title: "Graph"}]),
                        tasks: ref([{id: "loop-task", type: "kestra.Loop"}]),
                        subtitleResolvers: reactive({}),
                        manifestReady: ref(true),
                        hasResolvedComponent: () => true,
                        hasFailedComponent: () => false,
                    },
                },
            },
        })
        await flushPromises()

        // Then: iteration select reflects run-1
        const select = wrapper.findComponent({name: "KsSelect"})
        expect(select.props("modelValue")).toBe("run-1")

        // And: RemoteComponent fetchOutputs loads run-1
        const remote = wrapper.findComponent(RemoteStub)
        remote.props("fetchOutputs")()
        expect(loadTaskRunOutputsMock).toHaveBeenCalledWith("exec-1", "run-1")
    })

    it("shouldSynchronizeRouteQueryWhenUserSelectsAnotherIteration", async () => {
        // Given
        const execution = reactive({
            id: "exec-1",
            namespace: "ns",
            flowId: "flow",
            taskRunList: [
                {id: "run-1", taskId: "loop-task", value: "item-1"},
                {id: "run-2", taskId: "loop-task", value: "item-2"},
            ],
        })
        executionsStore.execution = execution

        const wrapper = i18nMount(Artifacts, {
            global: {
                provide: {
                    [EXECUTION_ARTIFACTS_KEY as symbol]: {
                        RemoteComponent: ref(RemoteStub),
                        artifacts: ref([{key: "loop-task:graph", taskId: "loop-task", taskType: "graph", cls: "kestra.Loop", title: "Graph"}]),
                        tasks: ref([{id: "loop-task", type: "kestra.Loop"}]),
                        subtitleResolvers: reactive({}),
                        manifestReady: ref(true),
                        hasResolvedComponent: () => true,
                        hasFailedComponent: () => false,
                    },
                },
            },
        })
        await flushPromises()

        // When: user selects run-1
        const select = wrapper.findComponent({name: "KsSelect"})
        select.vm.$emit("update:modelValue", "run-1")
        await flushPromises()

        // Then: route query is updated with iteration
        expect(routerState.replace).toHaveBeenCalledWith({
            query: expect.objectContaining({iteration: "run-1"}),
        })
    })

    it("shouldHideIterationSelectorForSingleRunTask", async () => {
        // Given: single-run task
        const execution = reactive({
            id: "exec-1",
            namespace: "ns",
            flowId: "flow",
            taskRunList: [
                {id: "run-1", taskId: "single-task"},
            ],
        })
        executionsStore.execution = execution

        const wrapper = i18nMount(Artifacts, {
            global: {
                provide: {
                    [EXECUTION_ARTIFACTS_KEY as symbol]: {
                        RemoteComponent: ref(RemoteStub),
                        artifacts: ref([{key: "single-task:graph", taskId: "single-task", taskType: "graph", cls: "kestra.Task", title: "Graph"}]),
                        tasks: ref([{id: "single-task", type: "kestra.Task"}]),
                        subtitleResolvers: reactive({}),
                        manifestReady: ref(true),
                        hasResolvedComponent: () => true,
                        hasFailedComponent: () => false,
                    },
                },
            },
        })
        await flushPromises()

        // Then: iteration select is not rendered
        expect(wrapper.find("[data-test='execution-artifact-iteration-select']").exists()).toBe(false)
    })

    it("shouldClearIterationQueryWhenSwitchingToDifferentTaskArtifact", async () => {
        // Given: active iteration on task-1
        routerState.route = reactive({params: {}, query: {artifact: "task-1:graph", iteration: "run-1"}})
        const execution = reactive({
            id: "exec-1",
            namespace: "ns",
            flowId: "flow",
            taskRunList: [
                {id: "run-1", taskId: "task-1"},
                {id: "run-2", taskId: "task-1"},
                {id: "run-3", taskId: "task-2"},
            ],
        })
        executionsStore.execution = execution

        const wrapper = i18nMount(Artifacts, {
            global: {
                provide: {
                    [EXECUTION_ARTIFACTS_KEY as symbol]: {
                        RemoteComponent: ref(RemoteStub),
                        artifacts: ref([
                            {key: "task-1:graph", taskId: "task-1", taskType: "graph", cls: "kestra.Task", title: "Task 1 Graph"},
                            {key: "task-2:graph", taskId: "task-2", taskType: "graph", cls: "kestra.Task", title: "Task 2 Graph"},
                        ]),
                        tasks: ref([
                            {id: "task-1", type: "kestra.Task"},
                            {id: "task-2", type: "kestra.Task"},
                        ]),
                        subtitleResolvers: reactive({}),
                        manifestReady: ref(true),
                        hasResolvedComponent: () => true,
                        hasFailedComponent: () => false,
                    },
                },
            },
        })
        await flushPromises()

        // When: clicking on task-2 artifact
        await wrapper.get("[data-test='execution-artifact-task-2:graph']").trigger("click")
        await flushPromises()

        // Then: iteration query param was cleared
        expect(routerState.replace).toHaveBeenCalledWith({
            query: {artifact: "task-2:graph"},
        })
    })

    it("shouldPreserveIterationQueryWhenSwitchingBetweenSiblingArtifactsOfSameTask", async () => {
        // Given: active iteration on task-1
        routerState.route = reactive({params: {}, query: {artifact: "task-1:graph", iteration: "run-1"}})
        const execution = reactive({
            id: "exec-1",
            namespace: "ns",
            flowId: "flow",
            taskRunList: [
                {id: "run-1", taskId: "task-1"},
                {id: "run-2", taskId: "task-1"},
            ],
        })
        executionsStore.execution = execution

        const wrapper = i18nMount(Artifacts, {
            global: {
                provide: {
                    [EXECUTION_ARTIFACTS_KEY as symbol]: {
                        RemoteComponent: ref(RemoteStub),
                        artifacts: ref([
                            {key: "task-1:graph", taskId: "task-1", taskType: "graph", cls: "kestra.Task", title: "Graph"},
                            {key: "task-1:summary", taskId: "task-1", taskType: "summary", cls: "kestra.Task", title: "Summary"},
                        ]),
                        tasks: ref([{id: "task-1", type: "kestra.Task"}]),
                        subtitleResolvers: reactive({}),
                        manifestReady: ref(true),
                        hasResolvedComponent: () => true,
                        hasFailedComponent: () => false,
                    },
                },
            },
        })
        await flushPromises()

        // When: switching to sibling artifact of the same task
        await wrapper.get("[data-test='execution-artifact-task-1:summary']").trigger("click")
        await flushPromises()

        // Then: iteration query param is preserved
        expect(routerState.replace).toHaveBeenCalledWith({
            query: {artifact: "task-1:summary", iteration: "run-1"},
        })
    })
})
