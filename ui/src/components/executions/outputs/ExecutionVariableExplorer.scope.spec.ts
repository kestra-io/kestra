import {beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {setActivePinia} from "pinia"
import * as OutputsAPI from "@kestra-io/kestra-sdk/outputs"
import ExecutionVariableExplorer from "./ExecutionVariableExplorer.vue"
import type {ExplorerSection} from "./SidebarList.vue"
import type {Execution} from "../../../stores/executions"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {routedPinia} from "../../../../tests/unit/memoryRouter"

vi.mock("@kestra-io/kestra-sdk/outputs", () => ({
    taskOutputsInformation: vi.fn().mockResolvedValue([]),
    taskRunOutputs: vi.fn().mockResolvedValue({}),
    executionOutputs: vi.fn().mockResolvedValue({}),
}))

const messages = {
    variable_explorer: {task_outputs: "Task outputs", tasks_outputs: "Tasks outputs", tree: "Tree", raw_json: "Raw JSON", search_placeholder: "Search"},
}

const execution = {id: "subflow-execution", namespace: "company", flowId: "child", flowRevision: 1, state: {current: "SUCCESS"}} as unknown as Execution

const globalConfig = {
    stubs: {
        KsEditor: true,
        KsNoData: true,
        KsSplitter: {template: "<div><slot /></div>"},
        KsSplitterPanel: {template: "<div><slot /></div>"},
        ExpressionDebugger: true,
    },
}

function mountScoped(outputs: Record<string, unknown>) {
    return i18nMount(ExecutionVariableExplorer, {
        messages,
        props: {scope: {execution, taskId: "dbt", outputs}},
        global: globalConfig,
    })
}

describe("ExecutionVariableExplorer scoped to one task", () => {
    beforeEach(() => {
        setActivePinia(routedPinia())
        vi.clearAllMocks()
    })

    test("shouldListOnlyTheKeysOfTheTaskOutputs", async () => {
        // When
        const wrapper = mountScoped({runId: 512994, manifest: "kestra:///manifest.json"})
        await flushPromises()

        // Then
        const sections = wrapper.findComponent({name: "SidebarList"}).props("sections") as ExplorerSection[]
        expect(sections).toHaveLength(1)
        expect(sections[0].label).toBe("Task outputs")
        expect(sections[0].items.map((item) => item.expression)).toEqual(["outputs.dbt.runId", "outputs.dbt.manifest"])
    })

    test("shouldSelectTheFirstKeyAndDebugItAgainstTheTaskExecution", async () => {
        // When
        const wrapper = mountScoped({runId: 512994, manifest: "kestra:///manifest.json"})
        await flushPromises()

        // Then
        const debuggerComponent = wrapper.findComponent({name: "ExpressionDebugger"})
        expect(debuggerComponent.props("expression")).toBe("{{ outputs.dbt.runId }}")
        expect(debuggerComponent.props("execution")).toEqual(execution)
    })

    test("shouldNotFetchTheExecutionWideOutputs", async () => {
        // When
        mountScoped({runId: 1})
        await flushPromises()

        // Then
        expect(OutputsAPI.taskOutputsInformation).not.toHaveBeenCalled()
        expect(OutputsAPI.executionOutputs).not.toHaveBeenCalled()
    })

    test("shouldKeepTheSelectedKeyWhenTheOutputsAreRefreshed", async () => {
        // Given
        const wrapper = mountScoped({runId: 1, manifest: "kestra:///manifest.json"})
        await flushPromises()
        await wrapper.findComponent({name: "SidebarList"}).vm.$emit("select", (wrapper.findComponent({name: "SidebarList"}).props("sections") as ExplorerSection[])[0].items[1])
        await flushPromises()

        // When
        await wrapper.setProps({scope: {execution, taskId: "dbt", outputs: {runId: 2, manifest: "kestra:///manifest.json"}}})
        await flushPromises()

        // Then
        expect(wrapper.findComponent({name: "ExpressionDebugger"}).props("expression")).toBe("{{ outputs.dbt.manifest }}")
    })
})
