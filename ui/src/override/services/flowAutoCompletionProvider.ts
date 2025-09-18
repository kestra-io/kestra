import {ComputedRef} from "vue";
import type {JSONSchema} from "@kestra-io/ui-libs";
import {YamlElement} from "@kestra-io/ui-libs";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import {QUOTE, YamlAutoCompletion} from "../../services/autoCompletionProvider";
import RegexProvider from "../../utils/regex";
import {State} from "@kestra-io/ui-libs";
import {usePluginsStore} from "../../stores/plugins";
import {useFlowStore} from "../../stores/flow";
import {useNamespacesStore} from "override/stores/namespaces";

function distinct<T>(val: T[] | undefined): T[] {
    return Array.from(new Set(val ?? []));
}

export class FlowAutoCompletion extends YamlAutoCompletion {
    flowsInputsCache: Record<string, string[]> = {};
    pluginsStore: ReturnType<typeof usePluginsStore>;
    flowStore: ReturnType<typeof useFlowStore>;
    namespacesStore: ReturnType<typeof useNamespacesStore>;
    private readonly completionSource: ComputedRef<string | undefined> | undefined;

    constructor(
        flowStore: ReturnType<typeof useFlowStore>,
        pluginsStore: ReturnType<typeof usePluginsStore>,
        namespacesStore: ReturnType<typeof useNamespacesStore>,
        completionSource?: ComputedRef<string | undefined>
    ) {
        super();
        this.flowStore = flowStore;
        this.pluginsStore = pluginsStore;
        this.namespacesStore = namespacesStore;
        this.completionSource = completionSource;
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
            "error",
            "kestra",
            "secret(namespace=${1:flow.namespace}, key=" + QUOTE + "${2:MY_SECRET}" + QUOTE + ")",
            "kv(namespace=${1:flow.namespace}, key=" + QUOTE + "${2:my_key}" + QUOTE + ")",
            "currentEachOutput(outputs=${1:outputs.forEach})",
            "decrypt(key=${1:secret('encryption_key')}, encrypted=${2:outputs.request.encryptedBody})",
            "encrypt(key=${1:secret('encryption_key')}, plaintext=${2:'value_to_encrypt'})",
            "errorLogs()",
            "fetchContext()",
            "isFileEmpty(namespace=${1:flow.namespace}, path=${2:outputs.download.uri})",
            "fileExists(namespace=${1:flow.namespace}, path=${2:outputs.download.uri})",
            "fileSize(namespace=${1:flow.namespace}, path=${2:outputs.download.uri})",
            "read(namespace=${1:flow.namespace}, path=${2:'a/namespace/file'})",
            "render(toRender=${1:inputs.inputWithPebble}, recursive=${2:true})",
            "renderOnce(toRender=${1:inputs.inputWithPebble})",
            "fileURI(path=${1:'a/namespace/file'})",
            "fromIon(ion=${1:read('ion/namespace/file')})",
            "fromJson(json=${1:read('json/namespace/file')})",
            "yaml(yaml=${1:inputs.yamlInput})",
            "uuid()",
            "id()",
            "now()",
            "randomInt(lower=${1:0}, upper=${2:10})",
            "randomPort()",
            "tasksWithState(state=${1:'FAILED'})",
            "http(uri=${1:'https://example.com'}, method=${2:'GET'})",
        ]);
    }

    private tasks(source: string): any[] {
        const tasksFromTasksProp = YAML_UTILS.extractFieldFromMaps(source, "tasks")
            .flatMap(allTasks => allTasks.tasks);
        const tasksFromTaskProp = YAML_UTILS.extractFieldFromMaps(source, "task")
            .map(task => task.task)
            .flatMap(task => YAML_UTILS.pairsToMap(task) ?? [])

        return [...tasksFromTasksProp, ...tasksFromTaskProp]
            .filter(task => typeof task?.get === "function" && task?.get("id"));
    }

    private async outputsFor(taskId: string, source: string): Promise<string[]> {
        const taskType = this.tasks(this.completionSource?.value ?? source).filter(task => task.get("id") === taskId)
            .map(task => task.get("type"))
            ?.[0];

        if (!taskType) {
            return [];
        }

        const pluginDoc = await this.pluginsStore.load({cls: taskType, commit: false});

        return Object.keys((pluginDoc?.schema as any)?.outputs?.properties ?? {});
    }

    private async triggerVars(flowAsJs?: {triggers?: {type: string}[]}): Promise<string[]> {
        if (flowAsJs === undefined) {
            return Promise.resolve([]);
        }

        const fetchTriggerVarsByType = await Promise.all(
            distinct(flowAsJs?.triggers?.map(trigger => trigger.type))
                .map(async triggerType => {
                    const triggerDoc: {schema: JSONSchema} | undefined = await this.pluginsStore.load({
                        cls: triggerType,
                        commit: false
                    });
                    return Object.keys(triggerDoc?.schema?.outputs?.properties ?? {});
                })
        );
        return distinct(fetchTriggerVarsByType.flat());
    }

    async nestedFieldAutoCompletion(source: string, parsed: any | undefined, parentField: string): Promise<string[]> {
        switch (parentField) {
            case "inputs":
                return Promise.resolve(parsed?.inputs?.map((input: {id?: string}) => input.id) ?? []);
            case "outputs":
                return Promise.resolve(parsed?.tasks?.map((task: {id?: string}) => task.id).filter(Boolean) ?? []);
            case "labels":
                return Promise.resolve(Object.keys(parsed?.labels ?? {}));
            case "flow":
                return Promise.resolve(["id", "namespace", "revision", "tenantId"]);
            case "execution":
                return Promise.resolve(["id", "startDate", "state", "originalId", "outputs"]);
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
            case "kestra":
                return Promise.resolve(["environment", "url"]);
            default: {
                const match = parentField.match(/^outputs\.([^.]+)$/);
                if (match) {
                    return await this.outputsFor(match[1], source);
                }

                return Promise.resolve([]);
            }
        }
    }

    private async subflowInputsAutoCompletion(namespace: string, flowId: string, revision: string | undefined, alreadyFilledInputs: string[]): Promise<string[]> {
        const subflowUid = namespace + "." + flowId + (revision === undefined ? "" : `:${revision}`) ;
        if (this.flowsInputsCache?.[subflowUid] === undefined) {
            try {
                const {inputs} = (await this.flowStore.loadFlow(
                    {
                        namespace,
                        id: flowId,
                        revision,
                        source: false,
                        store: false,
                        deleted: true
                    }
                ))
                this.flowsInputsCache[subflowUid] = inputs?.map((input: {id:string}) => `${input.id}`) ?? [];
            } catch {
                return [];
            }
        }

        return this.flowsInputsCache[subflowUid].filter(input => !alreadyFilledInputs.includes(input))
            .map(input => `${input}:`);
    }

    async valueAutoCompletion(_: string, parsed: any | undefined, yamlElement: YamlElement | undefined): Promise<string[]> {
        if (yamlElement === undefined) {
            return Promise.resolve([]);
        }

        const parentTask = yamlElement.parents?.[yamlElement.parents.length - 1];

        switch(yamlElement.key) {
            case "namespace": {
                const availableNamespaces = this.namespacesStore.autocomplete;
                return availableNamespaces === undefined
                    ? await this.namespacesStore.loadAutocomplete()
                    : Promise.resolve(availableNamespaces);
            }
            case "flowId": {
                if (parentTask !== undefined && parentTask.namespace !== undefined) {
                    let flowIds: string[] = (await this.flowStore.flowsByNamespace(parentTask.namespace))
                        .map((flow: {id: string}) => flow.id)
                    if (parsed?.id !== undefined && parsed?.namespace === parentTask.namespace) {
                        flowIds = flowIds.filter(flowId => flowId !== parsed?.id);
                    }
                    return Promise.resolve(flowIds);
                }

                break;
            }
            case "inputs": {
                if (parentTask !== undefined && parentTask.namespace !== undefined && parentTask.flowId !== undefined) {
                    return await this.subflowInputsAutoCompletion(parentTask.namespace, parentTask.flowId, parentTask.revision, Object.keys(yamlElement.value ?? {}));
                }
            }
        }

        return Promise.resolve([]);
    }

    private extractArgValue(arg: string | undefined) {
        if (arg === undefined) {
            return undefined;
        }

        const captureValue = new RegExp("^" + RegexProvider.captureStringValue + "$").exec(arg);
        if (!captureValue) {
            return undefined;
        }

        return captureValue?.[1];
    }

    async functionAutoCompletion(parsed: any | undefined, functionName: string, args: Record<string, string>): Promise<string[]> {
        let namespaceArg = args.namespace;
        if (namespaceArg === undefined || namespaceArg === "flow.namespace") {
           namespaceArg = parsed?.namespace === undefined ? "" : QUOTE + parsed.namespace + QUOTE;
        }
        switch (functionName) {
            case "secret": {
                const namespace = this.extractArgValue(namespaceArg);
                if (namespace === undefined) {
                    return Promise.resolve([]);
                }
                return Array.from(new Set<string>((await (this.namespacesStore as any).usableSecrets(namespace)).map((secret: string) => QUOTE + secret + QUOTE)));
            }
            case "kv": {
                const namespace = this.extractArgValue(namespaceArg);
                if (namespace === undefined) {
                    return Promise.resolve([]);
                }
                return (await this.namespacesStore.kvsList({id: namespace})).map((kv: {key: string}) => QUOTE + kv.key + QUOTE);
            }
            case "tasksWithState": {
                return State.arrayAllStates().map(({name}) => QUOTE + name + QUOTE);
            }
        }
        return Promise.resolve([]);
    }
}
