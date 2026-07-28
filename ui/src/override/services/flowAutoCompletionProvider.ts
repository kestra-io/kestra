import {ComputedRef} from "vue";
import type {JSONSchema} from "@kestra-io/ui-libs";
import {YamlElement} from "@kestra-io/ui-libs";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import {QUOTE, YamlAutoCompletion, type RootCompletionContext} from "../../services/autoCompletionProvider";
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

    rootFieldAutoCompletion(context?: RootCompletionContext): Promise<string[]> {
        const suggestions = [
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
        ];

        // subflow() blocks until the subflow terminates, so the backend only allows it at flow-input
        // render time; only suggest it inside a flow-root input's `values`/`expression`.
        if (this.isInputValuesContext(context)) {
            suggestions.push("subflow(namespace=${1:flow.namespace}, id=" + QUOTE + "${2:my_subflow}" + QUOTE + ")");
        }

        return Promise.resolve(suggestions);
    }

    private isInputValuesContext(context?: RootCompletionContext): boolean {
        if (context === undefined) {
            return false;
        }

        try {
            const localized = YAML_UTILS.localizeElementAtIndex(context.source, context.offset);
            if (localized === undefined || (localized.key !== "values" && localized.key !== "expression")) {
                return false;
            }

            const parents = localized.parents ?? [];
            const root: any = parents[0];
            const inputDefinition: any = parents[parents.length - 1];
            const rootInputs = root?.inputs;
            if (!Array.isArray(rootInputs)) {
                return false;
            }

            // confirm the enclosing map is one of the flow-root input definitions (excludes task
            // properties named `values` and trigger `inputs`, which are key/value, not definitions)
            return rootInputs.some((input: {id?: string}) => input?.id != null && input.id === inputDefinition?.id);
        } catch {
            return false;
        }
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

    private cursorProbeIndexes(source: string, cursorIndex: number): number[] {
        const safeCursorIndex = Math.max(0, Math.min(cursorIndex - 1, source.length - 1));
        const probeIndexes = [safeCursorIndex];
        let previousNonWhitespace = safeCursorIndex;
        while (previousNonWhitespace > 0 && /\s/.test(source.charAt(previousNonWhitespace))) {
            previousNonWhitespace--;
        }
        if (previousNonWhitespace !== safeCursorIndex) {
            probeIndexes.push(previousNonWhitespace);
        }

        return probeIndexes;
    }

    private taskIdFromCandidates(candidates: any[]): string | undefined {
        for (let i = candidates.length - 1; i >= 0; i--) {
            const candidate = candidates[i];
            if (
                candidate && typeof candidate === "object"
                && typeof candidate.id === "string"
                && typeof candidate.type === "string"
            ) {
                return candidate.id;
            }
        }

        return undefined;
    }

    private currentTaskIdAtCursor(source: string, cursorIndex?: number): string | undefined {
        if (cursorIndex === undefined || source.length === 0) {
            return undefined;
        }

        const probeIndexes = this.cursorProbeIndexes(source, cursorIndex);

        try {
            for (const probeIndex of probeIndexes) {
                const localized = YAML_UTILS.localizeElementAtIndex(source, probeIndex);
                const candidates = [...(localized?.parents ?? []), localized?.value];

                const taskId = this.taskIdFromCandidates(candidates);
                if (taskId) {
                    return taskId;
                }
            }
        } catch {
            return undefined;
        }

        return undefined;
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
                    }) as any;
                    return Object.keys(triggerDoc?.schema?.outputs?.properties ?? {});
                })
        );
        return distinct(fetchTriggerVarsByType.flat());
    }

    async nestedFieldAutoCompletion(source: string, parsed: any | undefined, parentField: string, cursorIndex?: number): Promise<string[]> {
        switch (parentField) {
            case "inputs":
                return Promise.resolve(parsed?.inputs?.map((input: {id?: string}) => input.id) ?? []);
            case "outputs": {
                const currentTaskId = this.currentTaskIdAtCursor(source, cursorIndex);
                return Promise.resolve(
                    parsed?.tasks
                        ?.map((task: {id?: string}) => task.id)
                        .filter((taskId: string | undefined) => taskId && taskId !== currentTaskId) ?? []
                );
            }
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

                // progressive autocomplete into a FORM group: `inputs.environment.` -> region, data_center
                const formMatch = parentField.match(/^inputs\.([^.]+)$/);
                if (formMatch) {
                    const form = parsed?.inputs?.find(
                        (input: {id?: string; type?: string}) => input.id === formMatch[1] && input.type === "FORM",
                    );
                    return Promise.resolve(form?.inputs?.map((child: {id?: string}) => child.id) ?? []);
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
                break;
            }
            case "pluginDefaultsRef": {
                return Promise.resolve(this.flowPluginDefaultsRefs(parsed));
            }
        }

        return Promise.resolve([]);
    }

    /**
     * Distinct `ref` ids declared in the flow's own `pluginDefaults` block.
     */
    protected flowPluginDefaultsRefs(parsed: any | undefined): string[] {
        const refs = (parsed?.pluginDefaults ?? [])
            .map((pluginDefault: {ref?: string}) => pluginDefault?.ref)
            .filter((ref: string | undefined): ref is string => Boolean(ref))
        return [...new Set<string>(refs)]
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
            case "subflow": {
                // subflow(namespace='...', id='...'): the arg under the cursor is the last one parsed
                const argNames = Object.keys(args);
                const currentArg = argNames[argNames.length - 1];
                if (currentArg === "namespace") {
                    const availableNamespaces = this.namespacesStore.autocomplete
                        ?? await this.namespacesStore.loadAutocomplete();
                    return availableNamespaces.map((namespace: string) => QUOTE + namespace + QUOTE);
                }
                if (currentArg === "id") {
                    const namespace = this.extractArgValue(namespaceArg);
                    if (namespace === undefined) {
                        return Promise.resolve([]);
                    }
                    let flowIds: string[] = (await this.flowStore.flowsByNamespace(namespace))
                        .map((flow: {id: string}) => flow.id);
                    // avoid suggesting the flow itself: subflow() on its own id recurses (depth-capped)
                    if (parsed?.id !== undefined && parsed?.namespace === namespace) {
                        flowIds = flowIds.filter(flowId => flowId !== parsed?.id);
                    }
                    return flowIds.map(flowId => QUOTE + flowId + QUOTE);
                }
                break;
            }
        }
        return Promise.resolve([]);
    }
}
