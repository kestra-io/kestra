import {describe, expect, it, vi, beforeAll} from "vitest"
import {FlowAutoCompletion} from "override/services/flowAutoCompletionProvider"
import {fillExpressionCache, functionToSnippet} from "../../../src/services/autoCompletionProvider"
import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"

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

const propertiesSchemaWrapper = (properties: Record<string, any>) => ({
    schema: {
        outputs: {
            properties,
        },
    },
})

const pluginsStore = {
    load: vi.fn((payload: any) =>{
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
} as any

const flowStore = {
    loadFlow: vi.fn(({namespace, id, revision}) => {
        if (namespace === "another.namespace" && id === "flow-other-namespace" && revision === 2) {
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
} as any

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
} as any

const mockFunctions = [
    {name: "kv", arguments: [{name: "key", defaultValue: "'my_key'"}, {name: "namespace", defaultValue: "flow.namespace"}, {name: "errorOnMissing", defaultValue: null}]},
    {name: "now", arguments: [{name: "format", defaultValue: null}, {name: "timeZone", defaultValue: null}, {name: "existingFormat", defaultValue: null}, {name: "locale", defaultValue: null}]},
    {name: "randomInt", arguments: [{name: "lower", defaultValue: "0"}, {name: "upper", defaultValue: "10"}]},
    {name: "secret", arguments: [{name: "key", defaultValue: "'MY_SECRET'"}, {name: "namespace", defaultValue: "flow.namespace"}, {name: "subkey", defaultValue: null}]},
    {name: "uuid", arguments: []},
]

const provider = new FlowAutoCompletion(flowStore, pluginsStore, namespacesStore)
const parsed = YAML_UTILS.parse(defaultFlow)
const flowWithOutputsAutocompleteInTaskParsed = YAML_UTILS.parse(flowWithOutputsAutocompleteInTask)

describe("FlowAutoCompletionProvider", () => {
    beforeAll(() => {
        fillExpressionCache([], mockFunctions)
    })

    it("root autocompletions include variables and function snippets", async () => {
        const result = await new FlowAutoCompletion(flowStore, pluginsStore, namespacesStore).rootFieldAutoCompletion()

        // Variables come first
        expect(result).toContain("outputs")
        expect(result).toContain("inputs")
        expect(result).toContain("kestra")

        // Function snippets are generated from functionsWithDefaults
        for (const fn of mockFunctions) {
            expect(result).toContain(functionToSnippet(fn))
        }
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

    it("function autocompletions", async () => {
        expect(await provider.functionAutoCompletion(parsed, "secret", {})).toEqual(["'myFirstSecret'", "'mySecondSecret'", "'myInheritedSecret'"])
        expect(await provider.functionAutoCompletion(parsed, "secret", {namespace: "'another.namespace'"})).toEqual(["'anotherNsFirstSecret'", "'anotherNsSecondSecret'"])
        expect(await provider.functionAutoCompletion(parsed, "kv", {})).toEqual(["'myFirstKv'", "'mySecondKv'"])
        expect(await provider.functionAutoCompletion(parsed, "kv", {namespace: "'another.namespace'"})).toEqual(["'anotherNsFirstKv'", "'anotherNsSecondKv'"])
    })
})
