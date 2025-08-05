import {h} from "vue";
import {ElMessageBox} from "element-plus";
import permission from "../models/permission";
import action from "../models/action";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import Utils from "../utils/utils";
import {editorViewTypes} from "../utils/constants";
import {apiUrl} from "override/utils/route";
import {useCoreStore} from "./core";
import {useEditorStore} from "./editor";

import {defineStore} from "pinia";
import {FlowGraph} from "@kestra-io/ui-libs/vue-flow-utils";

const textYamlHeader = {
    headers: {
        "Content-Type": "application/x-yaml"
    }
}

interface Trigger {
    id: string;
    type: string;
    backfill?: {
        start?: string;
    };
}

interface Task {
    id:string,
    type:string
}

interface Flow {
    id: string;
    namespace: string;
    source: string;
    revision?: number;
    deleted?: boolean;
    labels?: Record<string, string | boolean>;
    triggers?: Trigger[];
}

interface FlowState {
    flows?: Flow[];
    flow?: Flow;
    task?: Task;
    search?: any[];
    total: number;
    overallTotal?: number;
    flowGraph?: FlowGraph;
    invalidGraph: boolean;
    revisions?: any[];
    flowValidation?: { constraints: string, outdated?: boolean, infos?: string[] };
    taskError?: string;
    metrics: any[];
    aggregatedMetrics?: any;
    tasksWithMetrics: any[];
    executeFlow: boolean;
    lastSaveFlow?: string;
    isCreating: boolean;
    flowYaml?: string;
    flowYamlOrigin?: string;
    flowYamlBeforeAdd?: string;
    confirmOutdatedSaveDialog: boolean;
    haveChange: boolean;
    expandedSubflows: string[];
    metadata?: Record<string, any>;
}

export const useFlowStore = defineStore("flow", {
    state: (): FlowState => ({
        flows: undefined ,
        flow: undefined ,
        task: undefined ,
        search: undefined ,
        total: 0,
        overallTotal: undefined ,
        flowGraph: undefined ,
        invalidGraph: false,
        revisions: undefined,
        flowValidation: undefined,
        taskError: undefined,
        metrics: [],
        aggregatedMetrics: undefined,
        tasksWithMetrics: [],
        executeFlow: false,
        lastSaveFlow: undefined,
        isCreating: false,
        flowYaml: undefined,
        flowYamlOrigin: undefined,
        flowYamlBeforeAdd: undefined,
        confirmOutdatedSaveDialog: false,
        haveChange: false,
        expandedSubflows: [],
        metadata: undefined,
    }),

    actions: {
        onSaveMetadata(){
            this.flowYaml = YAML_UTILS.updateMetadata(this.flowYaml ?? "", this.metadata ?? {});
            this.metadata = undefined;
            this.haveChange = true;
        },
        async saveAll(){
            const editorStore = useEditorStore()
            const hasAnyDirtyTabs = editorStore.tabs.some(t => t.dirty === true);
            const hasChanges = this.haveChange || hasAnyDirtyTabs;

            if (this.flowErrors?.length || !hasChanges && !this.isCreating) {
                return;
            }

            if(!this.flow) return;
            await editorStore.saveAllTabs({namespace: this.flow.namespace});
            this.flowYamlOrigin = this.flowYaml;
            return this.saveWithoutRevisionGuard();
        },
        async save({content, namespace}: {content?: string, namespace?: string}) {
            const editorStore = useEditorStore()
            const hasAnyDirtyTabs = editorStore.tabs.some(t => t.dirty === true);
            const hasChanges = this.haveChange || hasAnyDirtyTabs;

            if (this.flowErrors?.length || !hasChanges && !this.isCreating) {
                return;
            }

            const source = this.flowYaml
            const currentTab = editorStore.current;

            if (this.isFlow && source) {
                return this.onEdit({source, currentIsFlow:true}).then((validation) => {
                    if (validation?.outdated && !this.isCreating) {
                        return "confirmOutdatedSaveDialog";
                    }
                    const res = this.saveWithoutRevisionGuard();
                    this.flowYamlOrigin = source;

                    if (currentTab && currentTab.name) {
                        editorStore.setTabDirty({
                            name: "Flow",
                            path: "Flow.yaml",
                            dirty: false,
                        });
                    }
                    return res
                });
            } else {
                if(!currentTab?.dirty) return;

                await this.vuexStore.dispatch("namespace/createFile", {
                    namespace: namespace ?? this.flow?.namespace,
                    path: currentTab.path ?? currentTab.name,
                    content,
                }, {root: true});
                editorStore.setTabDirty({
                    path: currentTab.path,
                    name: currentTab.name,
                    dirty: false
                });

                const coreStore = useCoreStore();
                coreStore.unsavedChange = false;
            }
        },
        async onEdit({source, currentIsFlow, editorViewType, topologyVisible}: {
            source: string,
            currentIsFlow: boolean,
            editorViewType?: string,
            topologyVisible?: boolean
        }) {
            const flowParsed = this.flowParsed;
            const currentTab = useEditorStore().current;

            if (currentIsFlow) {
                if (!source.trim()?.length) {
                    this.flowValidation = {
                        constraints: this.$i18n.t("flow must not be empty")
                    };
                    return
                }
                if (!this.isCreating && this.flow){
                    if(!source.trim()?.length ||
                        (flowParsed &&
                        (this.flow.id !== flowParsed.id ||
                            this.flow.namespace !== flowParsed.namespace)))
                        {
                        const coreStore = useCoreStore();
                        coreStore.message = {
                            variant: "error",
                            title: this.$i18n.t("readonly property"),
                            message: this.$i18n.t("namespace and id readonly"),
                        };
                        this.flowYaml = YAML_UTILS.replaceIdAndNamespace(
                            source,
                            this.flow.id,
                            this.flow.namespace
                        );
                    }
                }
            }

            this.haveChange = true;
            if(editorViewType === "YAML") {
                const coreStore = useCoreStore();
                coreStore.unsavedChange = true;
            }

            if(!this.isCreating){
                useEditorStore().setTabDirty({
                    ...currentTab,
                    name: currentTab?.name ?? "Flow",
                    path: currentTab?.path ?? "Flow.yaml",
                    dirty: true
                });
            }

            if(!currentIsFlow) return;

            return this.validateFlow({
                flow: (this.isCreating ? this.flowYaml : this.yamlWithNextRevision) ?? ""
            })
                .then((value) => {
                    if (
                        topologyVisible &&
                        this.flowHaveTasks &&
                        // avoid sending empty errors
                        // they make the backend fail
                        flowParsed && (!flowParsed.errors || flowParsed.errors.every(e => typeof e.id === "string"))
                    ) {
                        if(!value.constraints) this.fetchGraph();
                    }

                    return value;
                });
        },
        async saveWithoutRevisionGuard () {
            const flowYaml = this.flowYaml;
            const flowParsed = this.flowParsed;

            if (flowParsed === undefined) {
                const coreStore = useCoreStore();
                coreStore.message = {
                    variant: "error",
                    title: this.$i18n.t("invalid flow"),
                    message: this.$i18n.t("invalid yaml"),
                };

                return;
            }

            let overrideFlow = false;
            if (this.flowErrors) {
                if (this.flowValidation?.outdated && this.isCreating) {
                    overrideFlow = await ElMessageBox({
                        title: this.$i18n.t("override.title"),
                        message: () => {
                            return h("div", null, [
                                h("p", null, this.$i18n.t("override.details")),
                            ]);
                        },
                        showCancelButton: true,
                        confirmButtonText: this.$i18n.t("ok"),
                        cancelButtonText: this.$i18n.t("cancel"),
                        center: false,
                        showClose: false,
                    })
                        .then(() => {
                            overrideFlow = true;
                            return true;
                        })
                        .catch(() => {
                            return false;
                        });
                }
            }

            const {isCreating} = this;
            if (isCreating && !overrideFlow) {
                await this.createFlow({flow: flowYaml ?? ""})
                    .then((response) => {
                        this.$toast.bind({$t: this.$i18n.t})().saved(response.id);
                        const coreStore = useCoreStore();
                        coreStore.unsavedChange = false;
                        this.isCreating = false;
                        this.haveChange = false;
                    });
            } else {
                await this.saveFlow({flow: flowYaml})
                    .then((response) => {
                        this.$toast.bind({$t: this.$i18n.t})().saved(response.id);
                        const coreStore = useCoreStore();
                        coreStore.unsavedChange = false;
                    });
            }

            if (isCreating || overrideFlow) {
                return "redirect_to_update";
            }

            this.haveChange = false;
            await this.validateFlow({
                flow: (isCreating ? flowYaml : this.yamlWithNextRevision) ?? ""
            });
        },
        fetchGraph() {
            return this.loadGraphFromSource({
                flow: this.flowYaml ?? "",
                config: {
                    params: {
                        // due to usage of axios instance instead of $http which doesn't convert arrays
                        subflows: this.expandedSubflows.join(","),
                    },
                    validateStatus: (status) => {
                        return status === 200;
                    },
                },
            });
        },
        async initYamlSource({viewType}: {viewType: string}) {
            if(!this.flow) return;
            const {source} = this.flow;
            this.flowYaml = source;
            this.flowYamlOrigin = source;
            if (this.flowHaveTasks) {
                if (
                    [
                        editorViewTypes.TOPOLOGY,
                        editorViewTypes.SOURCE_TOPOLOGY,
                    ].includes(viewType)
                ) {
                    await this.fetchGraph();
                } else {
                    this.fetchGraph();
                }
            }

            // validate flow on first load
            return this.validateFlow({flow: this.isCreating ? source : this.yamlWithNextRevision})
        },
        findFlows(options: { [key: string]: any }) {
            const sortString = options.sort ? `?sort=${options.sort}` : ""
            delete options.sort
            return this.$http.get(`${apiUrl(this.vuexStore)}/flows/search${sortString}`, {
                params: options
            }).then(response => {
                this.flows = response.data.results
                this.total = response.data.total
                this.overallTotal = response.data.results.filter(f => f.namespace !== "tutorial").length

                return response.data;
            })
        },
        searchFlows(options: { [key: string]: any }) {
            const sortString = options.sort ? `?sort=${options.sort}` : ""
            delete options.sort
            return this.$http.get(`${apiUrl(this.vuexStore)}/flows/source${sortString}`, {
                params: options
            }).then(response => {
                this.search = response.data.results
                this.total = response.data.total

                return response.data;
            })
        },
        flowsByNamespace(namespace: string) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/flows/${namespace}`).then(response => {
                return response.data;
            })
        },
        loadFlow(options: {namespace: string, id: string, revision?: string, allowDeleted?: boolean, source?: boolean, store?: boolean, deleted?: boolean, httpClient?: any}) {
            const httpClient = options.httpClient ?? this.$http
            return httpClient.get(`${apiUrl(this.vuexStore)}/flows/${options.namespace}/${options.id}`,
                {
                    params: {
                        revision: options.revision,
                        allowDeleted: options.allowDeleted,
                        source: options.source === undefined ? true : undefined
                    },
                    validateStatus: (status: number) => {
                        return options.deleted ? status === 200 || status === 404 : status === 200;
                    }
                })
                .then((response: any) => {
                    if (response.data.exception) {
                        const coreStore = useCoreStore();
                        coreStore.message = {
                            title: "Invalid source code",
                            message: response.data.exception,
                            variant: "error"
                        };
                        // add this error to the list of errors
                        this.flowValidation = {
                            constraints: response.data.exception,
                            outdated: false,
                            infos: []
                        };
                        delete response.data.exception;
                    }
                    if(options.store === false) {
                        return response.data;
                    }

                    this.flow = response.data;
                    this.flowYaml = response.data.source;
                    this.flowYamlOrigin = response.data.source;
                    this.flowYamlBeforeAdd = response.data.source;
                    this.overallTotal = 1;

                    return response.data;
                })
        },
        loadTask(options: {namespace: string, id: string, taskId: string, revision?: string}) {
            return this.$http.get(
                `${apiUrl(this.vuexStore)}/flows/${options.namespace}/${options.id}/tasks/${options.taskId}${options.revision ? "?revision=" + options.revision : ""}`,
                {
                    validateStatus: (status) => {
                        return status === 200 || status === 404;
                    }
                }
            )
                .then(response => {
                    if (response.status === 200) {
                        this.task = response.data;

                        return response.data;
                    } else {
                        return null;
                    }
                })
        },
        saveFlow(options: {flow: string}) {
            const flowData = YAML_UTILS.parse(options.flow)
            return this.$http.put(`${apiUrl(this.vuexStore)}/flows/${flowData.namespace}/${flowData.id}`, options.flow, textYamlHeader)
                .then(response => {
                    if (response.status >= 300) {
                        return Promise.reject(new Error("Server error on flow save"))
                    } else {
                        this.flow = response.data;
                        useEditorStore().setTabDirty({
                            name: "Flow",
                            dirty: false,
                        });

                        return response.data;
                    }
                })
        },
        updateFlowTask(options: {flow: Flow, task: Task}) {
            return this.$http
                .patch(`${apiUrl(this.vuexStore)}/flows/${options.flow.namespace}/${options.flow.id}/${options.task.id}`, options.task).then(response => {
                    this.flow = response.data;

                    return response.data;
                })
                .then(flow => {
                    this.loadGraph( {flow});

                    return flow;
                })
        },
        createFlow(options: {flow: string}) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/flows`, options.flow, textYamlHeader).then(response => {
                this.flow = response.data;

                return response.data;
            })
        },
        deleteFlowAndDependencies(){
            const metadata = this.flowYamlMetadata;

            return new Promise((resolve, reject) => this.$http
                .get(
                    `${apiUrl(this.vuexStore)}/flows/${metadata.namespace}/${
                        metadata.id
                    }/dependencies`,
                    {params: {destinationOnly: true}}
                )
                .then((response) => {
                    let warning = "";

                    if (response.data && response.data.nodes) {
                        const deps = response.data.nodes
                            .filter(
                                (n) =>
                                    !(
                                        n.namespace === metadata.namespace &&
                                        n.id === metadata.id
                                    )
                            )
                            .map(
                                (n) =>
                                    "<li>" +
                                    n.namespace +
                                    ".<code>" +
                                    n.id +
                                    "</code></li>"
                            )
                            .join("\n");

                        if(deps.length){
                            warning =
                                "<div class=\"el-alert el-alert--warning is-light mt-3\" role=\"alert\">\n" +
                                "<div class=\"el-alert__content\">\n" +
                                "<p class=\"el-alert__description\">\n" +
                                this.$i18n.t("dependencies delete flow") +
                                "<ul>\n" +
                                deps +
                                "</ul>\n" +
                                "</p>\n" +
                                "</div>\n" +
                                "</div>";
                        }
                    }

                    return this.$i18n.t("delete confirm", {name: metadata.id}) + warning;
                })
                .then((message) => {
                    return this.$toast.bind({$t: this.$i18n.t})()
                        .confirm(message, () => {
                            resolve(this.deleteFlow(metadata));
                        })
                }).catch(reject)
            )
        },
        deleteFlow(flow: {namespace: string, id: string}) {
            return this.$http.delete(`${apiUrl(this.vuexStore)}/flows/${flow.namespace}/${flow.id}`).then(() => {
                this.flow = undefined;
            })
        },
        loadGraph(options: {flow: Flow, params?: any}) {
            const flow = options.flow;
            const params = options.params ? options.params : {};
            if (flow.revision) {
                params["revision"] = flow.revision;
            }
            return this.$http.get(`${apiUrl(this.vuexStore)}/flows/${flow.namespace}/${flow.id}/graph`, {params}).then(response => {
                this.invalidGraph = false;
                this.flowGraph = response.data;
                return response.data;
            }).catch(() => {
                this.invalidGraph = true;
            });
        },
        loadGraphFromSource(options: {flow: string, config?: any}) {
            const config = options.config ? {...options.config, ...textYamlHeader} : textYamlHeader;
            const flowParsed = YAML_UTILS.parse(options.flow);
            let flowSource = options.flow
            if (!flowParsed.id || !flowParsed.namespace) {
                flowSource = YAML_UTILS.updateMetadata(flowSource, {id: "default", namespace: "default"})
            }
            return this.$http.post(`${apiUrl(this.vuexStore)}/flows/graph`, flowSource, {...config, withCredentials: true})
                .then(response => {
                    this.flowGraph = response.data

                    const flow = YAML_UTILS.parse(options.flow);
                    flow.id = this.flow?.id ?? flow.id;
                    flow.namespace = this.flow?.namespace ?? flow.namespace;
                    flow.source = options.flow;
                    // prevent losing revision when loading graph from source
                    flow.revision = this.flow?.revision;
                    this.flow = flow;

                    return response;
                }).catch(error => {
                    if (error.response?.status === 422 && (!config?.params?.subflows || config?.params?.subflows?.length === 0)) {
                        return Promise.resolve(error.response);
                    }

                    if([404, 422].includes(error.response?.status) && config?.params?.subflows?.length > 0) {
                        const coreStore = useCoreStore();
                        coreStore.message = {
                            title: "Couldn't expand subflow",
                            message: error.response.data.message,
                            variant: "danger"
                        };
                    }

                    return Promise.reject(error);
                })
        },
        getGraphFromSourceResponse(options: {flow: string, config?: any}) {
            const config = options.config ? {...options.config, ...textYamlHeader} : textYamlHeader;
            const flowParsed = YAML_UTILS.parse(options.flow);
            let flowSource = options.flow
            if (!flowParsed.id || !flowParsed.namespace) {
                flowSource = YAML_UTILS.updateMetadata(flowSource, {id: "default", namespace: "default"})
            }
            return this.$http.post(`${apiUrl(this.vuexStore)}/flows/graph`, flowSource, {...config})
                .then(response => response.data)
        },
        loadRevisions(options: {namespace: string, id: string}) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/flows/${options.namespace}/${options.id}/revisions`).then(response => {
                this.revisions = response.data
                return response.data;
            })
        },
        exportFlowByIds(options: {ids: string[]}) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/flows/export/by-ids`, options.ids, {responseType: "blob"})
                .then(response => {
                    const blob = new Blob([response.data], {type: "application/octet-stream"});
                    const url = window.URL.createObjectURL(blob)
                    Utils.downloadUrl(url, "flows.zip");
                });
        },
        exportFlowByQuery(options: {namespace: string, id: string}) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/flows/export/by-query`, {params: options, headers: {"Accept": "application/octet-stream"}})
                .then(response => {
                    Utils.downloadUrl(response.request.responseURL, "flows.zip");
                });
        },
        importFlows(options: {file: File, namespace: string, override?: boolean}) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/flows/import`, Utils.toFormData(options), {
                headers: {"Content-Type": "multipart/form-data"}
            }).then(response => {
                return response;
            });
        },
        disableFlowByIds(options: {ids: string[]}) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/flows/disable/by-ids`, options.ids)
        },
        disableFlowByQuery(options: {namespace: string, id: string}) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/flows/disable/by-query`, options, {params: options})
        },
        enableFlowByIds(options: {ids: string[]}) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/flows/enable/by-ids`, options.ids)
        },
        enableFlowByQuery(options: {namespace: string, id: string}) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/flows/enable/by-query`, options, {params: options})
        },
        deleteFlowByIds(options: {ids: string[]}) {
            return this.$http.delete(`${apiUrl(this.vuexStore)}/flows/delete/by-ids`, {data: options.ids})
        },
        deleteFlowByQuery(options: {namespace: string, id: string}) {
            return this.$http.delete(`${apiUrl(this.vuexStore)}/flows/delete/by-query`, {params: options})
        },
        validateFlow(options: {flow: string}) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/flows/validate`, options.flow, {...textYamlHeader, withCredentials: true})
                .then(response => {
                    this.flowValidation = response.data[0]
                    return response.data[0]
                })
        },
        validateTask(options: {task: string, section: string}) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/flows/validate/task`, options.task, {...textYamlHeader, withCredentials: true, params: {section: options.section}})
                .then(response => {

                    this.taskError = response.data.constraints;
                    return response.data
                })
        },
        loadFlowMetrics(options: {namespace: string, id: string}) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/metrics/names/${options.namespace}/${options.id}`)
                .then(response => {
                    this.metrics = response.data
                    return response.data
                })
        },
        loadTaskMetrics(options: {namespace: string, id: string, taskId: string}) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/metrics/names/${options.namespace}/${options.id}/${options.taskId}`)
                .then(response => {
                    this.metrics = response.data
                    return response.data
                })
        },
        loadTasksWithMetrics(options: {namespace: string, id: string}) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/metrics/tasks/${options.namespace}/${options.id}`)
                .then(response => {
                    this.tasksWithMetrics = response.data
                    return response.data
                })
        },
        loadFlowAggregatedMetrics(options: {namespace: string, id: string, metric: string}) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/metrics/aggregates/${options.namespace}/${options.id}/${options.metric}`, {params: options})
                .then(response => {
                    this.aggregatedMetrics = response.data
                    return response.data
                })
        },
        loadTaskAggregatedMetrics(options: {namespace: string, id: string, taskId: string, metric: string}) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/metrics/aggregates/${options.namespace}/${options.id}/${options.taskId}/${options.metric}`, {params: options})
                .then(response => {
                    this.aggregatedMetrics = response.data
                    return response.data
                })
        },

        setTrigger({index, trigger}: {index: number, trigger: Trigger}) {
            const flow = this.flow ?? {} as Flow;

            if (flow.triggers === undefined) {
                flow.triggers = []
            }

            flow.triggers[index] = trigger;

            this.flow = {...flow}
        },
        removeTrigger(index: number) {
            const flow = this.flow ?? {} as Flow;
            flow.triggers?.splice(index, 1);

            this.flow = {...flow}
        },
        setExecuteFlow(value: boolean) {
            this.executeFlow = value;
        },
        addTrigger(trigger: Trigger) {
            const flow = this.flow ?? {} as Flow;

            if (trigger.backfill === undefined) {
                trigger.backfill = {
                    start: undefined
                }
            }

            if (flow.triggers === undefined) {
                flow.triggers = []
            }

            flow.triggers.push(trigger)

            this.flow = {...flow}
        },
    },
    getters: {
        isFlow(state) {
            const currentTab = useEditorStore().current;
            return currentTab?.flow !== undefined || state.isCreating;
        },
        isAllowedEdit(state): boolean {
            const store = this.vuexStore
            if (!state.flow || !store.getters["auth/user"]) {
                return false;
            }

            return store.getters["auth/user"].isAllowed(
                permission.FLOW,
                action.UPDATE,
                state.flow.namespace,
            );
        },
        readOnlySystemLabel(state) {
            if (!state.flow) {
                return false;
            }

            return (state.flow.labels?.["system.readOnly"] === "true") || (state.flow.labels?.["system.readOnly"] === true);
        },
        isReadOnly(state): boolean {
            return state.flow?.deleted || !this.isAllowedEdit || this.readOnlySystemLabel;
        },
        baseOutdatedTranslationKey(state) {
                const createOrUpdateKey = state.isCreating ? "create" : "update";
                return "outdated revision save confirmation." + createOrUpdateKey;
        },
        flowErrors(): string[] | undefined {
            if (this.isFlow) {
                const flowExistsError =
                    this.flowValidation?.outdated && this.isCreating
                        ? [`>>>>${this.baseOutdatedTranslationKey}`] // because translating is impossible here
                        : [];

                const constraintsError =
                    this.flowValidation?.constraints?.split(/, ?/) ?? [];

                const errors = [...flowExistsError, ...constraintsError];

                return errors.length === 0 ? undefined : errors;
            }

            return undefined;
        },
        flowInfos(state){
            if (this.isFlow) {
                const infos = state.flowValidation?.infos ?? [];

                return infos.length === 0 ? undefined : infos;
            }

            return undefined;
        },
        flowHaveTasks(state): boolean{
            if (this.isFlow) {
                const flow = state.isCreating ? state.flow?.source : state.flowYaml;
                return flow ? YAML_UTILS.flowHaveTasks(flow) : false;
            } else return false;
        },
        nextRevision(state){
            return (state.flow?.revision ?? 0) + 1;
        },
        yamlWithNextRevision(state): string{
            return `revision: ${this.nextRevision}\n${state.flowYaml}`;
        },
        flowParsed(state){
            try{
                return YAML_UTILS.parse(state.flowYaml)
            }catch{
                return undefined
            }
        },
        flowYamlMetadata(state){
            return YAML_UTILS.getMetadata(state.flowYaml ?? "");
        }
    }
})
