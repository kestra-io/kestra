import type {Store} from "vuex";
import type {JSONSchema} from "@kestra-io/ui-libs";
import YamlUtils from "../../utils/yamlUtils";
import uniqBy from "lodash/uniqBy";
import {YAMLMap} from "yaml";

export class YamlNoAutoCompletion {
    rootFieldAutoCompletion(): Promise<string[]> {
        return Promise.resolve([]);
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    nestedFieldAutoCompletion(source: string, parsed?: any, parentField: string): Promise<string[]> {
        return Promise.resolve([])
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    valueAutoCompletion(source: string, parsed?: any, cursorIndexInSource: number): Promise<string[]> {
        return Promise.resolve([]);
    }
}

export class FlowAutoCompletion extends YamlNoAutoCompletion{
    store: Store<Record<string, any>>;
    flowsInputsCache: Record<string, string[]> = {};

    constructor(store: Store<Record<string, any>>) {
        super();
        this.store = store;
    }

    rootFieldAutoCompletion(): Promise<string[]> {
        return Promise.resolve([
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
            "error"
        ]);
    }

    private tasks(source: string): YAMLMap<string, string> {
        const tasksFromTasksProp = YamlUtils.extractFieldFromMaps(source, "tasks")
            .flatMap(allTasks => allTasks.tasks);
        const tasksFromTaskProp = YamlUtils.extractFieldFromMaps(source, "task")
            .map(task => task.task)
            .flatMap(task => YamlUtils.pairsToMap(task) ?? [])

        return [...tasksFromTasksProp, ...tasksFromTaskProp]
            .filter(task => typeof task?.get === "function" && task?.get("id"));
    }

    private async outputsFor(taskId: string, source: string): Promise<string[]> {
        const taskType = this.tasks(source).filter(task => task.get("id") === taskId)
            .map(task => task.get("type"))
            ?.[0];

        if (!taskType) {
            return [];
        }

        const pluginDoc = await this.store.dispatch("plugin/load", {cls: taskType, commit: false});

        return Object.keys(pluginDoc?.schema?.outputs?.properties ?? {});
    }

    private async triggerVars(flowAsJs?: {triggers?: {type: string}[]}): Promise<string[]> {
        if (flowAsJs === undefined) {
            return Promise.resolve([]);
        }

        const fetchTriggerVarsByType = await Promise.all(
            uniqBy(flowAsJs?.triggers?.map(trigger => trigger.type))
                .map(async triggerType => {
                    const triggerDoc: {schema: JSONSchema} | undefined = await this.store.dispatch("plugin/load", {
                        cls: triggerType,
                        commit: false
                    });
                    return Object.keys(triggerDoc?.schema?.outputs?.properties ?? {});
                })
        );
        return uniqBy(fetchTriggerVarsByType.flat());
    }

    async nestedFieldAutoCompletion(source: string, parsed?: any, parentField: string): Promise<string[]> {
        switch (parentField) {
            case "inputs":
                return Promise.resolve(parsed?.inputs?.map(input => input.id) ?? []);
            case "outputs":
                return Promise.resolve(this.tasks(source).map(task => task.get("id")));
            case "labels":
                return Promise.resolve(Object.keys(parsed?.labels ?? {}));
            case "flow":
                return Promise.resolve(["id", "namespace", "revision", "tenantId"]);
            case "execution":
                return Promise.resolve(["id", "startDate", "originalId"]);
            case "vars":
                return Promise.resolve(Object.keys(parsed?.variables ?? {}));
            case "trigger":
                return await this.triggerVars(parsed);
            case "task":
                return Promise.resolve(["id", "type"]);
            case "taskrun":
                return Promise.resolve(["id", "startDate", "attemptsCount", "parentId", "value", "iteration"]);
            case "error":
                return Promise.resolve(["taskId", "message", "stackTrace"]);
            default: {
                const match = parentField.match(/^outputs\.([^.]+)$/);
                if (match) {
                    return await this.outputsFor(match[1], source);
                }

                return Promise.resolve([]);
            }
        }
    }

    private async subflowInputsAutoCompletion(namespace, flowId, revision, alreadyFilledInputs: string[]): Promise<string[]> {
        const subflowUid = namespace + "." + flowId;
        if (this.flowsInputsCache?.[subflowUid] === undefined) {
            try {
                this.flowsInputsCache[subflowUid] = (await this.store.dispatch(
                    "flow/loadFlow",
                    {
                        namespace: namespace,
                        id: flowId,
                        revision: revision,
                        source: false,
                        store: false,
                        deleted: true
                    }
                )).inputs?.map(input => `${input.id}`) ?? [];
            } catch {
                return [];
            }
        }

        return this.flowsInputsCache[subflowUid].filter(input => !alreadyFilledInputs.includes(input))
            .map(input => `${input}:`);
    }

    async valueAutoCompletion(source: string, parsed?: any, cursorIndexInSource: number): Promise<string[]> {
        const elementAtCursor = YamlUtils.localizeCursorParent(source, cursorIndexInSource);
        if (elementAtCursor === undefined) {
            return Promise.resolve([]);
        }

        const parentTask = elementAtCursor.parents?.[elementAtCursor.parents.length - 1];

        switch(elementAtCursor.key) {
            case "namespace": {
                const datatypeNamespaces = this.store.state["namespace"].datatypeNamespaces;
                return datatypeNamespaces === undefined
                    ? await this.store.dispatch("namespace/loadNamespacesForDatatype", {dataType: "flow"})
                    : Promise.resolve(datatypeNamespaces);
            }
            case "flowId": {
                if (parentTask !== undefined && parentTask.namespace !== undefined) {
                    let flowIds = (await this.store.dispatch("flow/flowsByNamespace", parentTask.namespace))
                        .map(flow => flow.id)
                    if (parsed?.id !== undefined && parsed?.namespace === parentTask.namespace) {
                        flowIds = flowIds.filter(flowId => flowId !== parsed?.id);
                    }
                    return Promise.resolve(flowIds);
                }

                break;
            }
            case "inputs": {
                if (parentTask !== undefined && parentTask.namespace !== undefined && parentTask.flowId !== undefined) {
                    return await this.subflowInputsAutoCompletion(parentTask.namespace, parentTask.flowId, parentTask.revision, Object.keys(elementAtCursor.value ?? {}));
                }
            }
        }

        return Promise.resolve([]);
    }
}