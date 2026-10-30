import {nextTick, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {setActivePinia} from "pinia"
import {beforeEach, describe, expect, it, vi} from "vitest"

import {routedPinia} from "../../../tests/unit/memoryRouter"
import type {Execution} from "../../stores/executions"
import type {Flow} from "../../stores/flow"
import {collectTaskTypes, useMetricDescriptions} from "./metricDescriptions"

const loadFlow = vi.fn()
const loadPlugin = vi.fn()

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({loadFlow}),
}))

vi.mock("../../stores/plugins", () => ({
    usePluginsStore: () => ({load: loadPlugin}),
}))

const SHELL = "io.kestra.plugin.scripts.shell.Commands"
const QUERY = "io.kestra.plugin.jdbc.postgresql.Query"

const execution = {namespace: "company.team", flowId: "metrics", flowRevision: 3} as Execution

function pluginWithMetrics(metrics: {name: string; description?: string}[]) {
    return {schema: {properties: {$metrics: metrics}}}
}

describe("collectTaskTypes", () => {
    it("maps every task id to its type, whatever flowable nests it", () => {
        const flow = {
            tasks: [
                {id: "if", type: "io.kestra.plugin.core.flow.If", then: [{id: "then", type: SHELL}], else: [{id: "else", type: SHELL}]},
                {id: "switch", type: "io.kestra.plugin.core.flow.Switch", cases: {a: [{id: "case", type: SHELL}]}, defaults: [{id: "default", type: SHELL}]},
                {id: "dag", type: "io.kestra.plugin.core.flow.Dag", tasks: [{task: {id: "dag-child", type: QUERY}, dependsOn: []}]},
            ],
            errors: [{id: "on-error", type: SHELL}],
            finally: [{id: "on-finally", type: SHELL}],
            afterExecution: [{id: "after", type: QUERY}],
        } as unknown as Flow

        const types = collectTaskTypes(flow)

        expect(Object.fromEntries(types)).toEqual({
            "if": "io.kestra.plugin.core.flow.If",
            "then": SHELL,
            "else": SHELL,
            "switch": "io.kestra.plugin.core.flow.Switch",
            "case": SHELL,
            "default": SHELL,
            "dag": "io.kestra.plugin.core.flow.Dag",
            "dag-child": QUERY,
            "on-error": SHELL,
            "on-finally": SHELL,
            "after": QUERY,
        })
    })
})

describe("useMetricDescriptions", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        setActivePinia(routedPinia())
    })

    it("describes a metric from the documentation of the plugin that emitted it", async () => {
        loadFlow.mockResolvedValue({tasks: [{id: "parent", type: "io.kestra.plugin.core.flow.Sequential", tasks: [{id: "query", type: QUERY}]}]})
        loadPlugin.mockResolvedValue(pluginWithMetrics([{name: "fetch.size", description: "Number of rows fetched."}]))

        const descriptionOf = useMetricDescriptions(() => execution, ref([{taskId: "query", name: "fetch.size"}]))
        await flushPromises()

        expect(loadFlow).toHaveBeenCalledWith(expect.objectContaining({namespace: "company.team", id: "metrics", revision: "3", store: false}))
        expect(loadPlugin).toHaveBeenCalledWith({cls: QUERY, commit: false})
        expect(descriptionOf({taskId: "query", name: "fetch.size"})).toBe("Number of rows fetched.")
        expect(descriptionOf({taskId: "query", name: "undocumented"})).toBeUndefined()
    })

    it("loads each plugin documentation once across pages of metrics", async () => {
        loadFlow.mockResolvedValue({tasks: [{id: "a", type: SHELL}, {id: "b", type: SHELL}]})
        loadPlugin.mockResolvedValue(pluginWithMetrics([]))
        const metrics = ref([{taskId: "a", name: "duration"}])

        useMetricDescriptions(() => execution, metrics)
        await flushPromises()
        metrics.value = [{taskId: "b", name: "duration"}]
        await nextTick()
        await flushPromises()

        expect(loadPlugin).toHaveBeenCalledTimes(1)
    })

    it("leaves metrics undescribed when the flow cannot be read", async () => {
        const forbidden = Object.assign(new Error("Forbidden"), {status: 403})
        loadFlow.mockRejectedValue(forbidden)

        const descriptionOf = useMetricDescriptions(() => execution, ref([{taskId: "query", name: "fetch.size"}]))
        await flushPromises()

        expect(loadPlugin).not.toHaveBeenCalled()
        expect(descriptionOf({taskId: "query", name: "fetch.size"})).toBeUndefined()
        expect(forbidden).toHaveProperty("__kestra_handled", true)
    })

    it("keeps a missing plugin out of the global error toast", async () => {
        const notFound = Object.assign(new Error("Not found"), {status: 404})
        loadFlow.mockResolvedValue({tasks: [{id: "query", type: QUERY}]})
        loadPlugin.mockRejectedValue(notFound)

        const descriptionOf = useMetricDescriptions(() => execution, ref([{taskId: "query", name: "fetch.size"}]))
        await flushPromises()

        expect(descriptionOf({taskId: "query", name: "fetch.size"})).toBeUndefined()
        expect(notFound).toHaveProperty("__kestra_handled", true)
    })
})
