import type {Store} from "vuex";
import {describe, expect, it, Mock, vi} from "vitest"
import {FlowAutoCompletion} from "override/services/flowAutoCompletionProvider";
import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

const defaultFlow = `inputs:
  - id: input1
    type: STRING
  - id: input2
    type: BOOLEAN
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
namespace: my.namespace`;

const propertiesSchemaWrapper = (properties: Record<string, any>) => ({
    schema: {
        outputs: {
            properties
        }
    }
})

interface MockStore<T> extends Store<T> {
    dispatch: Mock<() => Promise<any>>
}

const mockedStore: MockStore<Record<string, any>> = {
    state: {
        namespace: {}
    },
    dispatch: vi.fn((type, payload) => {
        if (type === "plugin/load") {
            switch (payload.cls) {
                case "io.kestra.plugin.core.trigger.Schedule":
                    return Promise.resolve(propertiesSchemaWrapper({
                        date: {},
                        next: {},
                        previous: {}
                    }))
                case "io.kestra.plugin.core.output.OutputValues":
                    return Promise.resolve(propertiesSchemaWrapper({
                        values: {}
                    }))
                case "io.kestra.plugin.core.kv.Get":
                    return Promise.resolve(propertiesSchemaWrapper({
                        value: {}
                    }))
                default:
                    return Promise.reject("404")
            }
        } else if (type === "namespace/loadNamespacesForDatatype" && payload.dataType === "flow") {
            return Promise.resolve(["my.namespace", "another.namespace"])
        } else if (type === "flow/flowsByNamespace") {
            if (payload === "another.namespace") {
                return Promise.resolve([{id: "flow-other-namespace"}, {id: "another-flow-other-namespace"}])
            } else {
                return Promise.reject("404")
            }
        } else if (type === "flow/loadFlow") {
            if (
                payload.namespace === "another.namespace" &&
                payload.id === "flow-other-namespace" &&
                payload.revision === 2 &&
                payload.source === false &&
                payload.store === false &&
                payload.deleted === true
            ) {
                return Promise.resolve({
                    inputs: [
                        {id: "first-input"},
                        {id: "second-input"}
                    ]
                })
            } else {
                return Promise.reject("404")
            }
        } else if (type === "namespace/inheritedSecrets") {
            if (payload.id === "my.namespace") {
                return Promise.resolve({"my.namespace": ["myFirstSecret", "mySecondSecret"], "my": ["myInheritedSecret"]});
            } else if (payload.id === "another.namespace") {
                return Promise.resolve({"another.namespace": ["anotherNsFirstSecret", "anotherNsSecondSecret"]});
            }
        } else if (type === "namespace/kvsList") {
            if (payload.id === "my.namespace") {
                return Promise.resolve([{key: "myFirstKv"}, {key: "mySecondKv"}]);
            } else if (payload.id === "another.namespace") {
                return Promise.resolve([{key: "anotherNsFirstKv"}, {key: "anotherNsSecondKv"}]);
            }
        }
        return Promise.reject("404")
    })
} as any

const provider = new FlowAutoCompletion(mockedStore);
const parsed = YAML_UTILS.parse(defaultFlow);

describe("FlowAutoCompletionProvider", () => {
    it("root autocompletions", async () => {
        expect(await new FlowAutoCompletion(mockedStore).rootFieldAutoCompletion()).toEqual([
            "outputs",
            "inputs",
            "vars",
            "flow",
            "execution",
            "trigger",
            "task",
            "taskrun",
            "labels",
            "envs",
            "globals",
            "parents",
            "error",
            "secret(namespace=${1:flow.namespace}, key='${2:MY_SECRET}')",
            "kv(namespace=${1:flow.namespace}, key='${2:my_key}')",
            "kestra"
        ]);
    })

    it("nested field autocompletions", async () => {
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "inputs")).toEqual(["input1", "input2"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "outputs")).toEqual(["task1", "task2", "subflow"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "labels")).toEqual(["myLabel1", "myLabel2"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "flow")).toEqual(["id", "namespace", "revision", "tenantId"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "execution")).toEqual(["id", "startDate", "state", "originalId"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "vars")).toEqual(["myVar1", "myVar2"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "trigger")).toEqual(["date", "next", "previous"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "task")).toEqual(["id", "type"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "taskrun")).toEqual(["id", "startDate", "attemptsCount", "parentId", "value", "iteration"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "error")).toEqual(["taskId", "message", "stackTrace"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "kestra")).toEqual(["environment", "url"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "outputs.task1")).toEqual(["values"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "outputs.task2")).toEqual(["value"]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "outputs.task3")).toEqual([]);
        expect(await provider.nestedFieldAutoCompletion(defaultFlow, parsed, "bad")).toEqual([]);
    })

    it("value autocompletions", async () => {
        mockedStore.dispatch.mockClear();

        expect(await provider.valueAutoCompletion(defaultFlow, parsed, YAML_UTILS.localizeElementAtIndex(defaultFlow, defaultFlow.indexOf("namespace:") + "namespace:".length))).toEqual(["my.namespace", "another.namespace"]);
        expect(await provider.valueAutoCompletion(defaultFlow, parsed, YAML_UTILS.localizeElementAtIndex(defaultFlow, defaultFlow.indexOf("flowId:") + "flowId:".length))).toEqual(["flow-other-namespace", "another-flow-other-namespace"]);

        expect(mockedStore.dispatch.mock.calls.length).toBe(2);
        const firstInputIndex = defaultFlow.indexOf("first-input");
        expect(await provider.valueAutoCompletion(defaultFlow, parsed, YAML_UTILS.localizeElementAtIndex(defaultFlow, firstInputIndex))).toEqual(["second-input:"]);
        expect(mockedStore.dispatch.mock.calls.length).toBe(3);
        // Subflow inputs cache kicks in
        expect(await provider.valueAutoCompletion(defaultFlow, parsed, YAML_UTILS.localizeElementAtIndex(defaultFlow, firstInputIndex))).toEqual(["second-input:"]);
        expect(mockedStore.dispatch.mock.calls.length).toBe(3);

        // With newline already inserted
        expect(await provider.valueAutoCompletion(defaultFlow.substring(0, firstInputIndex) + "\n        " + defaultFlow.substring(firstInputIndex, defaultFlow.length), parsed, YAML_UTILS.localizeElementAtIndex(defaultFlow, firstInputIndex))).toEqual(["second-input:"]);
    })

    it("function autocompletions", async () => {
        expect(await provider.functionAutoCompletion(parsed, "secret", {})).toEqual(["'myFirstSecret'", "'mySecondSecret'", "'myInheritedSecret'"]);
        expect(await provider.functionAutoCompletion(parsed, "secret", {namespace: "'another.namespace'"})).toEqual(["'anotherNsFirstSecret'", "'anotherNsSecondSecret'"]);
        expect(await provider.functionAutoCompletion(parsed, "kv", {})).toEqual(["'myFirstKv'", "'mySecondKv'"]);
        expect(await provider.functionAutoCompletion(parsed, "kv", {namespace: "'another.namespace'"})).toEqual(["'anotherNsFirstKv'", "'anotherNsSecondKv'"]);
    })
})
