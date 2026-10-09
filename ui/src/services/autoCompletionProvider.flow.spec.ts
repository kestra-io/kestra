import {describe, expect, it, vi, beforeAll, beforeEach} from "vitest"
import {FlowAutoCompletion, type NamespacesStoreLike} from "override/services/flowAutoCompletionProvider"
import {fillExpressionCache, functionToSnippet} from "./autoCompletionProvider"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import type {ParsedFlow, useFlowStore} from "../stores/flow"
import type {usePluginsStore} from "../stores/plugins"
import type {useMcpStore} from "../stores/mcp"
import type {useDashboardStore} from "../stores/dashboard"

const defaultFlow = `inputs:
  - id: input1
    type: STRING
  - id: input2
    type: BOOL
labels:
  myLabel1: "myLabelValue1"
  myLabel2: "myLabelValue2"
variables:
  myVar1: "myValue1"
  myVar2: "myValue2"
tasks:
  - id: task1
    type: io.kestra.plugin.core.output.OutputValues
    values:
      myInput1: "{{ inputs.input1 }}"
  - id: task2
    type: io.kestra.plugin.core.kv.Get
    key: "myKey"
  - id: subflow
    type: io.kestra.plugin.core.flow.Subflow
    namespace: another.namespace
    flowId: flow-other-namespace
    revision: 2
    inputs:
      first-input: "value1"
triggers:
  - id: schedule
    type: io.kestra.plugin.core.trigger.Schedule
    cron: "* * * * *"
id: my-flow
namespace: my.namespace`

const flowWithDashboardExportTask = [
    "tasks:",
    "  - id: dashboardExport",
    "    type: io.kestra.plugin.kestra.dashboards.Export",
    "    dashboardId: my-dashboard",
    "    chartId: my-chart",
    "  - id: dashboardExportNoDashboard",
    "    type: io.kestra.plugin.kestra.dashboards.Export",
    "    chartId: my-chart",
    "id: my-flow",
    "namespace: my.namespace",
].join("\n")

const flowWithOutputsAutocompleteInTask = [
    "tasks:",
    "  - id: download",
    "    type: io.kestra.plugin.core.http.Download",
    "    uri: https://example.com/file.txt",
    "  - id: filter",
    "    type: io.kestra.plugin.core.storage.FilterItems",
    "    from: \"{{ outputs. }}\"",
    "  - id: upload",
    "    type: io.kestra.plugin.core.storage.Upload",
    "    from: \"{{ outputs.download.uri }}\"",
    "id: my-flow",
    "namespace: my.namespace",
].join("\n")

const flowWithErrorsAndFinally = [
    "id: flow_8929727",
    "namespace: company.team",
    "tasks:",
    "  - id: normal_task",
    "    type: io.kestra.plugin.core.output.OutputValues",
    "    values:",
    "      hello: world",
    "errors:",
    "  - id: error_task",
    "    type: io.kestra.plugin.core.kv.Get",
    "    key: hello",
    "  - id: log",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: \"{{ outputs. }}\"",
    "finally:",
    "  - id: finally_task",
    "    type: io.kestra.plugin.core.kv.Get",
    "    key: bye",
].join("\n")

const propertiesSchemaWrapper = (properties: Record<string, unknown>) => ({
    schema: {
        outputs: {
            properties,
        },
    },
})

const pluginsStore = {
    allTypes: [
        "io.kestra.plugin.core.output.OutputValues",
        "io.kestra.plugin.core.kv.Get",
        "io.kestra.plugin.core.flow.Subflow",
        "io.kestra.plugin.core.http.Download",
        "io.kestra.plugin.core.storage.FilterItems",
        "io.kestra.plugin.core.storage.Upload",
        "io.kestra.plugin.core.flow.WorkingDirectory",
        "io.kestra.plugin.git.Clone",
        "io.kestra.plugin.core.execution.Assert",
        "io.kestra.plugin.core.flow.If",
        "io.kestra.plugin.core.flow.Pause",
        "io.kestra.plugin.core.log.Log",
        "io.kestra.plugin.core.trigger.Schedule",
    ],
    load: vi.fn((payload: Parameters<ReturnType<typeof usePluginsStore>["load"]>[0]) =>{
        switch (payload.cls) {
                case "io.kestra.plugin.core.trigger.Schedule":
                    return Promise.resolve(propertiesSchemaWrapper({
                        date: {},
                        next: {},
                        previous: {},
                    }))
                case "io.kestra.plugin.core.output.OutputValues":
                    return Promise.resolve(propertiesSchemaWrapper({
                        values: {},
                    }))
                case "io.kestra.plugin.core.kv.Get":
                    return Promise.resolve(propertiesSchemaWrapper({
                        value: {},
                    }))
                default:
                    return Promise.reject("404")
            }
    }),
}

const flowStore = {
    loadFlow: vi.fn(({namespace, id, revision}) => {
        if (namespace === "another.namespace" && id === "flow-other-namespace" && revision === "2") {
            return Promise.resolve({
                inputs: [
                    {id: "first-input"},
                    {id: "second-input"},
                ],
            })
        }
        return Promise.reject("404")
    }),
    loadGraphFromSource: vi.fn(() => Promise.resolve({
        nodes: [
            {id: "task1", type: "io.kestra.plugin.core.output.OutputValues"},
            {id: "task2", type: "io.kestra.plugin.core.kv.Get"},
            {id: "subflow", type: "io.kestra.plugin.core.flow.Subflow"},
            {id: "schedule", type: "io.kestra.plugin.core.trigger.Schedule"},
        ],
        edges: [
            {source: "task1", target: "task2"},
            {source: "task2", target: "subflow"},
            {source: "subflow", target: "schedule"},
        ],
    })),
    flowsByNamespace: vi.fn((namespace: string) => {
        if (namespace === "my.namespace") {
            return Promise.resolve([{id: "my-flow", namespace: "my.namespace"}])
        } else if (namespace === "another.namespace") {
            return Promise.resolve([{id: "flow-other-namespace", namespace: "another.namespace"}, {id: "another-flow-other-namespace", namespace: "another.namespace"}])
        }
        return Promise.reject("404")
    }),
}

const namespacesStore = {
    datatypeNamespaces: undefined,
    loadAutocomplete: vi.fn(() => ["my.namespace", "another.namespace"]),
    usableSecrets: vi.fn((id: string) => {
        if (id === "my.namespace") {
            return ["myFirstSecret", "mySecondSecret", "myInheritedSecret"]
        } else if (id === "another.namespace") {
            return ["anotherNsFirstSecret", "anotherNsSecondSecret"]
        }
        return []
    }),
    kvsList: vi.fn((params: {id: string}) => {
        if (params.id === "my.namespace") {
            return [{key: "myFirstKv"}, {key: "mySecondKv"}]
        } else if (params.id === "another.namespace") {
            return [{key: "anotherNsFirstKv"}, {key: "anotherNsSecondKv"}]
        }
        return []
    }),
}

const mcpStore = {
    list: vi.fn(() => Promise.resolve({results: [{id: "default"}, {id: "analytics-server"}], total: 2})),
}

const dashboardStore = {
    searchIds: vi.fn(() => Promise.resolve([{id: "my-dashboard", title: "My Dashboard"}, {id: "other-dashboard", title: "Other"}])),
    chartsById: vi.fn((id: string) => {
        if (id === "my-dashboard") {
            return Promise.resolve([
                {id: "my-chart", type: "io.kestra.plugin.core.dashboard.chart.Bar"},
                {id: "markdown-chart", type: "io.kestra.plugin.core.dashboard.chart.Markdown"},
            ])
        }
        if (id === "_default") {
            return Promise.resolve([{id: "default-chart", type: "io.kestra.plugin.core.dashboard.chart.Table"}])
        }
        return Promise.resolve([])
    }),
}

const mockFunctions = [
    {name: "kv", arguments: [{name: "key", defaultValue: "'my_key'"}, {name: "namespace", defaultValue: "flow.namespace"}, {name: "errorOnMissing", defaultValue: null}]},
    {name: "now", arguments: [{name: "format", defaultValue: null}, {name: "timeZone", defaultValue: null}, {name: "existingFormat", defaultValue: null}, {name: "locale", defaultValue: null}]},
    {name: "randomInt", arguments: [{name: "lower", defaultValue: "0"}, {name: "upper", defaultValue: "10"}]},
    {name: "secret", arguments: [{name: "key", defaultValue: "'MY_SECRET'"}, {name: "namespace", defaultValue: "flow.namespace"}, {name: "subkey", defaultValue: null}]},
    {name: "uuid", arguments: []},
    {name: "subflow", arguments: [{name: "namespace", defaultValue: null}, {name: "id", defaultValue: null}]},
]

type ProviderParsedFlow = NonNullable<Parameters<FlowAutoCompletion["valueAutoCompletion"]>[1]>

function newProvider() {
    return new FlowAutoCompletion(
        flowStore as unknown as ReturnType<typeof useFlowStore>,
        pluginsStore as unknown as ReturnType<typeof usePluginsStore>,
        namespacesStore as unknown as NamespacesStoreLike,
        mcpStore as unknown as ReturnType<typeof useMcpStore>,
        dashboardStore as unknown as ReturnType<typeof useDashboardStore>,
    )
}

let provider: FlowAutoCompletion
const parsed = YAML_UTILS.parse<ProviderParsedFlow>(defaultFlow)
const flowWithOutputsAutocompleteInTaskParsed = YAML_UTILS.parse<ProviderParsedFlow>(flowWithOutputsAutocompleteInTask)
const flowWithErrorsAndFinallyParsed = YAML_UTILS.parse<ProviderParsedFlow>(flowWithErrorsAndFinally)

describe("FlowAutoCompletionProvider", () => {
    beforeAll(() => {
        fillExpressionCache([], mockFunctions)
    })

    // Several tests assert call counts on the store mocks and rely on a cold subflow
    // cache, so both the spies and the provider must start fresh — otherwise the file
    // only passes in declaration order.
    beforeEach(() => {
        vi.clearAllMocks()
        provider = newProvider()
    })

    it("root autocompletions include variables and function snippets", async () => {
        const result = await newProvider().rootFieldAutoCompletion()

        // Variables come first
        expect(result).toContain("outputs")
        expect(result).toContain("inputs")
        expect(result).toContain("kestra")
        expect(result).toContain("item")

        // Function snippets are generated from functionsWithDefaults
        for (const fn of mockFunctions.filter(fn => fn.name !== "subflow")) {
            expect(result).toContain(functionToSnippet(fn))
        }

        // subflow() is input-only: without a values/expression context it must not be suggested
        expect(result).not.toContain("subflow()")
    })

    it("subflow() is suggested only inside a flow-root input's values/expression", async () => {
        const flow = `id: scoped-flow
namespace: my.namespace
inputs:
  - id: region
    type: SELECT
    expression: "SUBFLOW_IN_INPUT"
tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
    message: "SUBFLOW_IN_TASK"`

        // Inside the input's `expression` → suggested
        const inInput = await provider.rootFieldAutoCompletion({source: flow, offset: flow.indexOf("SUBFLOW_IN_INPUT")})
        expect(inInput).toContain("subflow()")

        // Inside a task property → not suggested
        const inTask = await provider.rootFieldAutoCompletion({source: flow, offset: flow.indexOf("SUBFLOW_IN_TASK")})
        expect(inTask).not.toContain("subflow()")
        // other functions are still suggested everywhere
        expect(inTask).toContain("uuid()")
    })

    it("functionToSnippet generates correct named-argument snippets", () => {
        expect(functionToSnippet({name: "uuid", arguments: []})).toBe("uuid()")
        expect(functionToSnippet({name: "randomInt", arguments: [{name: "lower", defaultValue: "0"}, {name: "upper", defaultValue: "10"}]}))
            .toBe("randomInt(lower=${1:0}, upper=${2:10})")
        expect(functionToSnippet({name: "secret", arguments: [{name: "key", defaultValue: "'MY_SECRET'"}, {name: "namespace", defaultValue: "flow.namespace"}, {name: "subkey", defaultValue: null}]}))
            .toBe("secret(key=${1:'MY_SECRET'}, namespace=${2:flow.namespace})")
        expect(functionToSnippet({name: "now", arguments: [{name: "format", defaultValue: null}, {name: "timeZone", defaultValue: null}]}))
            .toBe("now()")
    })

    it("nested field autocompletions", async () => {
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "inputs")).toEqual(["input1", "input2"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "outputs")).toEqual(["task1", "task2", "subflow"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "labels")).toEqual(["myLabel1", "myLabel2"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "flow")).toEqual(["id", "namespace", "revision", "tenantId"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "execution")).toEqual(["id", "startDate", "state", "originalId", "outputs"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "vars")).toEqual(["myVar1", "myVar2"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "trigger")).toEqual(["date", "next", "previous"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "task")).toEqual(["id", "type"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "taskrun")).toEqual(["id", "startDate", "attemptsCount", "parentId", "value", "iteration"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "error")).toEqual(["taskId", "message", "stackTrace"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "kestra")).toEqual(["environment", "url"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "outputs.task1")).toEqual(["values"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "outputs.task2")).toEqual(["value"])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "outputs.task3")).toEqual([])
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "bad")).toEqual([])
    })

    it("outputs autocomplete includes tasks from errors and finally blocks", async () => {
        const cursorIndex = flowWithErrorsAndFinally.indexOf("outputs.") + "outputs.".length
        expect(cursorIndex).toBeGreaterThan(0)

        // Inside the log task in errors: suggests tasks from tasks, errors, finally (excluding current log task)
        expect(await provider.nestedFieldAutoCompletion(
            flowWithErrorsAndFinally,
            flowWithErrorsAndFinallyParsed,
            "outputs",
            cursorIndex,
        )).toEqual(["normal_task", "error_task", "finally_task"])

        // Without cursor: suggests all task IDs across blocks
        expect(await provider.nestedFieldAutoCompletion(
            flowWithErrorsAndFinally,
            flowWithErrorsAndFinallyParsed,
            "outputs",
        )).toEqual(["normal_task", "error_task", "log", "finally_task"])

        // Output properties for task in errors and finally blocks
        expect(await provider.nestedFieldAutoCompletion(
            flowWithErrorsAndFinally,
            flowWithErrorsAndFinallyParsed,
            "outputs.error_task",
        )).toEqual(["value"])
        expect(await provider.nestedFieldAutoCompletion(
            flowWithErrorsAndFinally,
            flowWithErrorsAndFinallyParsed,
            "outputs.finally_task",
        )).toEqual(["value"])
    })

    it("outputs autocomplete excludes current task id", async () => {
        const cursorIndex = flowWithOutputsAutocompleteInTask.indexOf("outputs.") + "outputs.".length
        expect(cursorIndex).toBeGreaterThan(0)

        expect(await provider.nestedFieldAutoCompletion(
            flowWithOutputsAutocompleteInTask,
            flowWithOutputsAutocompleteInTaskParsed,
            "outputs",
            cursorIndex,
        )).toEqual(["download", "upload"])

        expect(await provider.nestedFieldAutoCompletion(
            flowWithOutputsAutocompleteInTask,
            flowWithOutputsAutocompleteInTaskParsed,
            "outputs",
        )).toEqual(["download", "filter", "upload"])
    })

    it("value autocompletions", async () => {
        expect(await provider.valueAutoCompletion(defaultFlow, parsed, YAML_UTILS.localizeElementAtIndex(defaultFlow, defaultFlow.indexOf("namespace:") + "namespace:".length))).toEqual(["my.namespace", "another.namespace"])
        expect(await provider.valueAutoCompletion(defaultFlow, parsed, YAML_UTILS.localizeElementAtIndex(defaultFlow, defaultFlow.indexOf("flowId:") + "flowId:".length))).toEqual(["flow-other-namespace", "another-flow-other-namespace"])

        expect(namespacesStore.loadAutocomplete).toHaveBeenCalledOnce()
        expect(flowStore.flowsByNamespace).toHaveBeenCalledWith("another.namespace")
        const firstInputIndex = defaultFlow.indexOf("first-input")
        namespacesStore.loadAutocomplete.mockClear()
        expect(await provider.valueAutoCompletion(defaultFlow, parsed, YAML_UTILS.localizeElementAtIndex(defaultFlow, firstInputIndex))).toEqual(["second-input:"])
        expect(namespacesStore.loadAutocomplete).not.toHaveBeenCalled()
        expect(flowStore.loadFlow).toHaveBeenCalledOnce()

        // Subflow inputs cache kicks in
        expect(await provider.valueAutoCompletion(defaultFlow, parsed, YAML_UTILS.localizeElementAtIndex(defaultFlow, firstInputIndex))).toEqual(["second-input:"])
        expect(flowStore.loadFlow).toHaveBeenCalledOnce()

        // With newline already inserted
        expect(await provider.valueAutoCompletion(defaultFlow.substring(0, firstInputIndex) + "\n        " + defaultFlow.substring(firstInputIndex, defaultFlow.length), parsed, YAML_UTILS.localizeElementAtIndex(defaultFlow, firstInputIndex))).toEqual(["second-input:"])
    })

    it("dashboardId/chartId autocompletions", async () => {
        const flow = flowWithDashboardExportTask
        const parsedFlow = YAML_UTILS.parse<ProviderParsedFlow>(flow)

        expect(await provider.valueAutoCompletion(flow, parsedFlow, YAML_UTILS.localizeElementAtIndex(flow, flow.indexOf("dashboardId:") + "dashboardId:".length))).toEqual(["my-dashboard", "other-dashboard"])

        // chartId depends on the sibling dashboardId and excludes non-exportable (Markdown) charts
        const firstChartIdIndex = flow.indexOf("chartId:")
        expect(await provider.valueAutoCompletion(flow, parsedFlow, YAML_UTILS.localizeElementAtIndex(flow, firstChartIdIndex + "chartId:".length))).toEqual(["my-chart"])

        // chartId stays live even when dashboardId is missing: falls back to the "_default" sentinel dashboard
        const secondChartIdIndex = flow.lastIndexOf("chartId:")
        expect(await provider.valueAutoCompletion(flow, parsedFlow, YAML_UTILS.localizeElementAtIndex(flow, secondChartIdIndex + "chartId:".length))).toEqual(["default-chart"])
    })

    it("function autocompletions", async () => {
        expect(await provider.functionAutoCompletion(parsed, "secret", {})).toEqual(["'myFirstSecret'", "'mySecondSecret'", "'myInheritedSecret'"])
        expect(await provider.functionAutoCompletion(parsed, "secret", {namespace: "'another.namespace'"})).toEqual(["'anotherNsFirstSecret'", "'anotherNsSecondSecret'"])
        expect(await provider.functionAutoCompletion(parsed, "kv", {})).toEqual(["'myFirstKv'", "'mySecondKv'"])
        expect(await provider.functionAutoCompletion(parsed, "kv", {namespace: "'another.namespace'"})).toEqual(["'anotherNsFirstKv'", "'anotherNsSecondKv'"])
    })

    it("subflow function autocompletions suggest namespaces and flow ids", async () => {
        // editing the `namespace` arg → all namespaces, quoted (Monaco does the prefix filtering)
        expect(await provider.functionAutoCompletion(parsed, "subflow", {namespace: "'m"})).toEqual(["'my.namespace'", "'another.namespace'"])
        // editing the `id` arg → flow ids of the chosen namespace, quoted
        expect(await provider.functionAutoCompletion(parsed, "subflow", {namespace: "'another.namespace'", id: "'fl"})).toEqual(["'flow-other-namespace'", "'another-flow-other-namespace'"])
        // editing the `id` arg in the flow's own namespace excludes the flow itself (avoids self-recursion)
        expect(await provider.functionAutoCompletion(parsed, "subflow", {namespace: "'my.namespace'", id: "'m"})).toEqual([])
    })
})
