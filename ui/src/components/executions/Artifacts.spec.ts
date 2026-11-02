import {describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {reactive, ref} from "vue"

const executionsStore = vi.hoisted(() => ({execution: null as unknown}))

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, query: {}}),
    useRouter: () => ({replace: vi.fn()}),
}))

vi.mock("../../stores/executions", () => ({useExecutionsStore: () => executionsStore}))
vi.mock("../../stores/plugins", () => ({usePluginsStore: () => ({fetchIcons: vi.fn()})}))
vi.mock("../../composables/useTaskRunOutputs", () => ({loadTaskRunOutputs: vi.fn(() => Promise.resolve({}))}))
vi.mock("../plugins/TaskIcon.vue", () => ({default: {template: "<i />"}}))

import Artifacts from "./Artifacts.vue"
import {EXECUTION_ARTIFACTS_KEY} from "./composables/useExecutionArtifacts"
import {i18nMount} from "../../../tests/unit/i18nMount"

const deferred = <T,>() => {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((r) => { resolve = r })
    return {promise, resolve}
}

describe("Artifacts", () => {
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
})
