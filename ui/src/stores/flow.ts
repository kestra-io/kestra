import {computed, h, ref} from "vue";
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
import {Store, useStore} from "vuex";
import {makeToast} from "../utils/toast";
import {InputType} from "../utils/inputs";
import {globalI18n} from "../translations/i18n";
import {transformResponse} from "../components/dependencies/composables/useDependencies";
import {useNamespacesStore} from "override/stores/namespaces";

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
    id: string,
    type: string
}

interface Input {
    id: string;
    type: InputType;
    required?: boolean;
    defaults?: any;
}

interface FlowValidations {
    constraints?: string;
    outdated?: boolean;
    infos?: string[];
    warnings?: string[];
    deprecationPaths?: string[];
}

interface Flow {
    id: string;
    namespace: string;
    source: string;
    revision?: number;
    deleted?: boolean;
    labels?: Record<string, string | boolean>;
    triggers?: Trigger[];
    inputs?: Input[];
    errors: { message: string; code?: string, id?: string }[];
}

export const useFlowStore = defineStore("flow", () => {
    const flows = ref<Flow[]>()
    const flow = ref<Flow>()
    const task = ref<Task>()
    const search = ref<any[]>()
    const total = ref<number>(0)
    const overallTotal = ref<number>()
    const flowGraph = ref<FlowGraph>()
    const invalidGraph = ref<boolean>(false)
    const revisions = ref<any[]>()
    const flowValidation = ref<FlowValidations>()
    const taskError = ref<string>()
    const metrics = ref<any[]>()
    const aggregatedMetrics = ref<any>()
    const tasksWithMetrics = ref<any[]>()
    const executeFlow = ref<boolean>(false)
    const lastSaveFlow = ref<string>()
    const isCreating = ref<boolean>(false)
    const flowYaml = ref<string>("")
    const flowYamlOrigin = ref<string>("")
    const flowYamlBeforeAdd = ref<string>("")
    const confirmOutdatedSaveDialog = ref<boolean>(false)
    const haveChange = ref<boolean>(false)
    const expandedSubflows = ref<string[]>([])
    const metadata = ref<Record<string, any>>()

    const store = useStore() as Store<any> & {
        $http: {
            put: (url: string, data?: any, config?: any) => Promise<any>;
            post: (url: string, data?: any, config?: any) => Promise<any>;
            get: (url: string, config?: any) => Promise<any>;
            delete: (url: string, config?: any) => Promise<any>;
            patch: (url: string, data?: any, config?: any) => Promise<any>;
        }
    };

    const t = (key: string, values?: Record<string, any>) => {
        if (!globalI18n.value) {
            return key;
        }
        return (values ? globalI18n.value?.t(key, values) : globalI18n.value?.t(key)) ?? key;
    };

    function onSaveMetadata() {
        flowYaml.value = YAML_UTILS.updateMetadata(flowYaml.value ?? "", metadata.value ?? {});
        metadata.value = undefined;
        haveChange.value = true;
    }

    async function saveAll() {
        const editorStore = useEditorStore()
        const hasAnyDirtyTabs = editorStore.tabs.some(t => t.dirty === true);
        const hasChanges = haveChange.value || hasAnyDirtyTabs;

        if (flowErrors.value?.length || !hasChanges && !isCreating.value) {
            return;
        }

        if (!flow.value) return;
        await editorStore.saveAllTabs({namespace: flow.value.namespace});
        flowYamlOrigin.value = flowYaml.value;
        return saveWithoutRevisionGuard();
    }

    const namespaceStore = useNamespacesStore()

    async function save({content, namespace}: { content?: string, namespace?: string }) {
        const editorStore = useEditorStore()
        const hasAnyDirtyTabs = editorStore.tabs.some(t => t.dirty === true);
        const hasChanges = haveChange.value || hasAnyDirtyTabs;

        if (flowErrors.value?.length || !hasChanges && !isCreating.value) {
            return;
        }

        const source = flowYaml.value;
        const currentTab = editorStore.current;

        if (isFlow.value && source) {
            return onEdit({source, currentIsFlow: true}).then((validation: any) => {
                if (validation?.outdated && !isCreating.value) {
                    return "confirmOutdatedSaveDialog";
                }
                const res = saveWithoutRevisionGuard();
                flowYamlOrigin.value = source;

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
            if (!currentTab?.dirty) return;

            await namespaceStore.createFile({
                namespace: namespace ?? flow.value?.namespace ?? "",
                path: currentTab.path ?? currentTab.name,
                content: content ?? "",
            });
            editorStore.setTabDirty({
                path: currentTab.path,
                name: currentTab.name,
                dirty: false
            });

            const coreStore = useCoreStore();
            coreStore.unsavedChange = false;
        }
    }

    async function onEdit({source, currentIsFlow, editorViewType, topologyVisible}: {
        source: string,
        currentIsFlow: boolean,
        editorViewType?: string,
        topologyVisible?: boolean
    }) {
        const flowParsed = flow.value;
        const currentTab = useEditorStore().current;

        if (currentIsFlow) {
            if (!source.trim()?.length) {
                flowValidation.value = {
                    constraints: t("flow must not be empty")
                };
                return
            }
            if (!isCreating.value && flow.value) {
                if (!source.trim()?.length ||
                    (flowParsed &&
                        (flow.value.id !== flowParsed.id ||
                            flow.value.namespace !== flowParsed.namespace))) {
                    const coreStore = useCoreStore();
                    coreStore.message = {
                        variant: "error",
                        title: t("readonly property"),
                        message: t("namespace and id readonly"),
                    };
                    flowYaml.value = YAML_UTILS.replaceIdAndNamespace(
                        source,
                        flow.value.id,
                        flow.value.namespace
                    );
                }
            }
        }

        haveChange.value = true;
        if (editorViewType === "YAML") {
            const coreStore = useCoreStore();
            coreStore.unsavedChange = true;
        }

        if (!isCreating.value) {
            useEditorStore().setTabDirty({
                ...currentTab,
                name: currentTab?.name ?? "Flow",
                path: currentTab?.path ?? "Flow.yaml",
                dirty: true
            });
        }

        if (!currentIsFlow) return;

        return validateFlow({
            flow: (isCreating.value ? flowYaml.value : yamlWithNextRevision.value) ?? ""
        })
            .then((value: {constraints?: any}) => {
                if (
                    topologyVisible &&
                    flowHaveTasks.value &&
                    // avoid sending empty errors
                    // they make the backend fail
                    flowParsed && (!flowParsed.errors || flowParsed.errors.every(e => typeof e.id === "string"))
                ) {
                    if (!value.constraints) fetchGraph();
                }

                return value;
            });
    }

    const toast = makeToast(t);

    async function saveWithoutRevisionGuard() {
        const flowSource = flowYaml.value ?? "";

        if (flowParsed.value === undefined) {
            const coreStore = useCoreStore();
            coreStore.message = {
                variant: "error",
                title: t("invalid flow"),
                message: t("invalid yaml"),
            };

            return;
        }

        let overrideFlow = false;
        if (flowErrors.value) {
            if (flowValidation.value?.outdated && isCreating.value) {
                overrideFlow = await ElMessageBox({
                    title: t("override.title"),
                    message: () => {
                        return h("div", null, [
                            h("p", null, t("override.details")),
                        ]);
                    },
                    showCancelButton: true,
                    confirmButtonText: t("ok"),
                    cancelButtonText: t("cancel"),
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

        const isCreatingBackup = isCreating.value;
        if (isCreating.value && !overrideFlow) {
            await createFlow({flow: flowSource ?? ""})
                .then((response: Flow) => {
                    toast.saved(response.id);
                    const coreStore = useCoreStore();
                    coreStore.unsavedChange = false;
                    isCreating.value = false;
                    haveChange.value = false;
                });
        } else {
            await saveFlow({flow: flowSource})
                .then((response: Flow) => {
                    toast.saved(response.id);
                    const coreStore = useCoreStore();
                    coreStore.unsavedChange = false;
                });
        }

        if (isCreatingBackup || overrideFlow) {
            return "redirect_to_update";
        }

        haveChange.value = false;
        await validateFlow({
            flow: (isCreatingBackup ? flowSource : yamlWithNextRevision.value) ?? ""
        });
    }
    function fetchGraph() {
        return loadGraphFromSource({
            flow: flowYaml.value ?? "",
            config: {
                params: {
                    // due to usage of axios instance instead of $http which doesn't convert arrays
                    subflows: expandedSubflows.value.join(","),
                },
                validateStatus: (status: number) => {
                    return status === 200;
                },
            },
        });
    }

    async function initYamlSource({viewType}: { viewType: string }) {
        if (!flow.value) return;
        const {source} = flow.value;
        flowYaml.value = source;
        flowYamlOrigin.value = source;
        if (flowHaveTasks.value) {
            if (
                [
                    editorViewTypes.TOPOLOGY,
                    editorViewTypes.SOURCE_TOPOLOGY,
                ].includes(viewType)
            ) {
                await fetchGraph();
            } else {
                fetchGraph();
            }
        }

        // validate flow on first load
        return validateFlow({flow: isCreating.value ? source : yamlWithNextRevision.value})
    }

    function findFlows(options: { [key: string]: any }) {
        const sortString = options.sort ? `?sort=${options.sort}` : ""
        delete options.sort
        return store.$http.get(`${apiUrl(store)}/flows/search${sortString}`, {
            params: options
        }).then(response => {
            flows.value = response.data.results
            total.value = response.data.total
            overallTotal.value = response.data.results.filter((f: any) => f.namespace !== "tutorial").length

            return response.data;
        })
    }
    function searchFlows(options: { [key: string]: any }) {
        const sortString = options.sort ? `?sort=${options.sort}` : ""
        delete options.sort
        return store.$http.get(`${apiUrl(store)}/flows/source${sortString}`, {
            params: options
        }).then(response => {
            search.value = response.data.results
            total.value = response.data.total

            return response.data;
        })
    }

    function flowsByNamespace(namespace: string) {
        return store.$http.get(`${apiUrl(store)}/flows/${namespace}`).then(response => {
            return response.data;
        })
    }

    function loadFlow(options: { namespace: string, id: string, revision?: string, allowDeleted?: boolean, source?: boolean, store?: boolean, deleted?: boolean, httpClient?: any }) {
        const httpClient = options.httpClient ?? store.$http
        return httpClient.get(`${apiUrl(store)}/flows/${options.namespace}/${options.id}`,
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
                    flowValidation.value = {
                        constraints: response.data.exception,
                        outdated: false,
                        infos: []
                    };
                    delete response.data.exception;
                }
                if (options.store === false) {
                    return response.data;
                }

                flow.value = response.data;
                flowYaml.value = response.data.source;
                flowYamlOrigin.value = response.data.source;
                flowYamlBeforeAdd.value = response.data.source;
                overallTotal.value = 1;

                return response.data;
            })
    }
    function loadTask(options: { namespace: string, id: string, taskId: string, revision?: string }) {
        return store.$http.get(
            `${apiUrl(store)}/flows/${options.namespace}/${options.id}/tasks/${options.taskId}${options.revision ? "?revision=" + options.revision : ""}`,
            {
                validateStatus: (status: number) => {
                    return status === 200 || status === 404;
                }
            }
        )
            .then(response => {
                if (response.status === 200) {
                    task.value = response.data;

                    return response.data;
                } else {
                    return null;
                }
            })
    }
    function saveFlow(options: { flow: string }) {
        const flowData = YAML_UTILS.parse(options.flow)
        return store.$http.put(`${apiUrl(store)}/flows/${flowData.namespace}/${flowData.id}`, options.flow, textYamlHeader)
            .then(response => {
                if (response.status >= 300) {
                    return Promise.reject(new Error("Server error on flow save"))
                } else {
                    flow.value = response.data;
                    useEditorStore().setTabDirty({
                        name: "Flow",
                        dirty: false,
                    });

                    return response.data;
                }
            })
    }
    function updateFlowTask(options: { flow: Flow, task: Task }) {
        return store.$http
            .patch(`${apiUrl(store)}/flows/${options.flow.namespace}/${options.flow.id}/${options.task.id}`, options.task).then(response => {
                flow.value = response.data;

                return response.data;
            })
            .then(flow => {
                loadGraph({flow});

                return flow;
            })
    }

    function createFlow(options: { flow: string }) {
        return store.$http.post(`${apiUrl(store)}/flows`, options.flow, textYamlHeader).then(response => {
            flow.value = response.data;

            return response.data;
        })
    }

    function loadDependencies(options: { namespace: string, id: string, subtype: "FLOW" | "EXECUTION" }) {
        return store.$http.get(`${apiUrl(store)}/flows/${options.namespace}/${options.id}/dependencies?expandAll=true`).then(response => {
            return {
                data: transformResponse(response.data, options.subtype),
                count: response.data.nodes ? [...new Set(response.data.nodes.map((r:{uid:string}) => r.uid))].length : 0
            };
        })
    }

function deleteFlowAndDependencies() {
    const metadata = flowYamlMetadata.value;

    return store.$http
        .get(
            `${apiUrl(store)}/flows/${metadata.namespace}/${metadata.id}/dependencies`,
            {params: {destinationOnly: true}}
        )
        .then((response) => {
            let warning = "";
            if (response.data && response.data.nodes) {
                const deps = response.data.nodes
                    .filter(
                        (n: any) =>
                            !(
                                n.namespace === metadata.namespace &&
                                n.id === metadata.id
                            )
                    )
                    .map(
                        (n: any) =>
                            "<li>" +
                            n.namespace +
                            ".<code>" +
                            n.id +
                            "</code></li>"
                    )
                    .join("\n");

                if (deps.length) {
                    warning =
                        "<div class=\"el-alert el-alert--warning is-light mt-3\" role=\"alert\">\n" +
                        "<div class=\"el-alert__content\">\n" +
                        "<p class=\"el-alert__description\">\n" +
                        t("dependencies delete flow") +
                        "<ul>\n" +
                        deps +
                        "</ul>\n" +
                        "</p>\n" +
                        "</div>\n" +
                        "</div>";
                }
            }
            return t("delete confirm", {name: metadata.id}) + warning;
        })
        .then((message) => {
            return new Promise((resolve, reject) => {
                toast.confirm(message, () => {
                    return deleteFlow({namespace: metadata.namespace, id: metadata.id}).then(resolve).catch(reject);
                }, "warning");
            });
        })
        .catch(error => {
            return Promise.reject(error);
        });
}

    function deleteFlow(options: { namespace: string, id: string }) {
        return store.$http.delete(`${apiUrl(store)}/flows/${options.namespace}/${options.id}`).then(() => {
            flow.value = undefined;
        })
    }

    function loadGraph(options: { flow: Flow, params?: any }) {
        const flowVar = options.flow;
        const params = options.params ? options.params : {};
        if (flowVar.revision) {
            params["revision"] = flowVar.revision;
        }
        return store.$http.get(`${apiUrl(store)}/flows/${flowVar.namespace}/${flowVar.id}/graph`, {params}).then(response => {
            invalidGraph.value = false;
            flowGraph.value = response.data;
            return response.data;
        }).catch(() => {
            invalidGraph.value = true;
        });
    }
    function loadGraphFromSource(options: { flow: string, config?: any }) {
        const config = options.config ? {...options.config, ...textYamlHeader} : textYamlHeader;
        const flowParsed = YAML_UTILS.parse(options.flow);
        let flowSource = options.flow
        if (!flowParsed.id || !flowParsed.namespace) {
            flowSource = YAML_UTILS.updateMetadata(flowSource, {id: "default", namespace: "default"})
        }
        return store.$http.post(`${apiUrl(store)}/flows/graph`, flowSource, {...config, withCredentials: true})
            .then(response => {
                flowGraph.value = response.data

                const flowVar = YAML_UTILS.parse(options.flow);
                flowVar.id = flow.value?.id ?? flowVar.id;
                flowVar.namespace = flow.value?.namespace ?? flowVar.namespace;
                flowVar.source = options.flow;
                // prevent losing revision when loading graph from source
                flowVar.revision = flow.value?.revision;
                flow.value = flowVar;

                return response;
            }).catch(error => {
                if (error.response?.status === 422 && (!config?.params?.subflows || config?.params?.subflows?.length === 0)) {
                    return Promise.resolve(error.response);
                }

                if ([404, 422].includes(error.response?.status) && config?.params?.subflows?.length > 0) {
                    const coreStore = useCoreStore();
                    coreStore.message = {
                        title: "Couldn't expand subflow",
                        message: error.response.data.message,
                        variant: "danger"
                    };
                }

                return Promise.reject(error);
            })
    }

    function getGraphFromSourceResponse(options: { flow: string, config?: any }) {
        const config = options.config ? {...options.config, ...textYamlHeader} : textYamlHeader;
        const flowParsed = YAML_UTILS.parse(options.flow);
        let flowSource = options.flow
        if (!flowParsed.id || !flowParsed.namespace) {
            flowSource = YAML_UTILS.updateMetadata(flowSource, {id: "default", namespace: "default"})
        }
        return store.$http.post(`${apiUrl(store)}/flows/graph`, flowSource, {...config})
            .then(response => response.data)
    }

    function loadRevisions(options: { namespace: string, id: string, store?: boolean }) {
        return store.$http.get(`${apiUrl(store)}/flows/${options.namespace}/${options.id}/revisions`).then(response => {
            if (options.store !== false) {
                revisions.value = response.data
            }
            return response.data;
        })
    }

    function exportFlowByIds(options: { ids: string[] }) {
        return store.$http.post(`${apiUrl(store)}/flows/export/by-ids`, options.ids, {responseType: "blob"})
            .then(response => {
                const blob = new Blob([response.data], {type: "application/octet-stream"});
                const url = window.URL.createObjectURL(blob)
                Utils.downloadUrl(url, "flows.zip");
            });
    }

    function exportFlowByQuery(options: { namespace: string, id: string }) {
        return store.$http.get(`${apiUrl(store)}/flows/export/by-query`, {params: options, headers: {"Accept": "application/octet-stream"}})
            .then(response => {
                Utils.downloadUrl(response.request.responseURL, "flows.zip");
            });
    }
    function importFlows(options: { file: File, namespace: string, override?: boolean }) {
        return store.$http.post(`${apiUrl(store)}/flows/import`, Utils.toFormData(options), {
            headers: {"Content-Type": "multipart/form-data"}
        }).then(response => {
            return response;
        });
    }
    function disableFlowByIds(options: { ids: string[] }) {
        return store.$http.post(`${apiUrl(store)}/flows/disable/by-ids`, options.ids)
    }
    function disableFlowByQuery(options: { namespace: string, id: string }) {
        return store.$http.post(`${apiUrl(store)}/flows/disable/by-query`, options, {params: options})
    }
    function enableFlowByIds(options: { ids: string[] }) {
        return store.$http.post(`${apiUrl(store)}/flows/enable/by-ids`, options.ids)
    }
    function enableFlowByQuery(options: { namespace: string, id: string }) {
        return store.$http.post(`${apiUrl(store)}/flows/enable/by-query`, options, {params: options})
    }
    function deleteFlowByIds(options: { ids: string[] }) {
        return store.$http.delete(`${apiUrl(store)}/flows/delete/by-ids`, {data: options.ids})
    }
    function deleteFlowByQuery(options: { namespace: string, id: string }) {
        return store.$http.delete(`${apiUrl(store)}/flows/delete/by-query`, {params: options})
    }
    function validateFlow(options: { flow: string }) {
        return store.$http.post(`${apiUrl(store)}/flows/validate`, options.flow, {...textYamlHeader, withCredentials: true})
            .then(response => {
                flowValidation.value = response.data[0]
                return response.data[0]
            })
    }
    function validateTask(options: { task: string, section: string }) {
        return store.$http.post(`${apiUrl(store)}/flows/validate/task`, options.task, {...textYamlHeader, withCredentials: true, params: {section: options.section}})
            .then(response => {
                taskError.value = response.data.constraints;
                return response.data
            })
    }
    function loadFlowMetrics(options: { namespace: string, id: string }) {
        return store.$http.get(`${apiUrl(store)}/metrics/names/${options.namespace}/${options.id}`)
            .then(response => {
                metrics.value = response.data
                return response.data
            })
    }
    function loadTaskMetrics(options: { namespace: string, id: string, taskId: string }) {
        return store.$http.get(`${apiUrl(store)}/metrics/names/${options.namespace}/${options.id}/${options.taskId}`)
            .then(response => {
                metrics.value = response.data
                return response.data
            })
    }
    function loadTasksWithMetrics(options: { namespace: string, id: string }) {
        return store.$http.get(`${apiUrl(store)}/metrics/tasks/${options.namespace}/${options.id}`)
            .then(response => {
                tasksWithMetrics.value = response.data
                return response.data
            })
    }
    function loadFlowAggregatedMetrics(options: { namespace: string, id: string, metric: string }) {
        return store.$http.get(`${apiUrl(store)}/metrics/aggregates/${options.namespace}/${options.id}/${options.metric}`, {params: options})
            .then(response => {
                aggregatedMetrics.value = response.data
                return response.data
            })
    }
    function loadTaskAggregatedMetrics(options: { namespace: string, id: string, taskId: string, metric: string }) {
        return store.$http.get(`${apiUrl(store)}/metrics/aggregates/${options.namespace}/${options.id}/${options.taskId}/${options.metric}`, {params: options})
            .then(response => {
                aggregatedMetrics.value = response.data
                return response.data
            })
    }

    function setTrigger({index, trigger}: { index: number, trigger: Trigger }) {
        const flowVar = flow.value ?? {} as Flow;

        if (flowVar.triggers === undefined) {
            flowVar.triggers = []
        }

        flowVar.triggers[index] = trigger;

        flow.value = {...flowVar}
    }

    function removeTrigger(index: number) {
        const flowVar = flow.value ?? {} as Flow;
        flowVar.triggers?.splice(index, 1);

        flow.value = {...flowVar}
    }

    function setExecuteFlow(value: boolean) {
        executeFlow.value = value;
    }

    function addTrigger(trigger: Trigger) {
        const flowVar = flow.value ?? {} as Flow;

        if (trigger.backfill === undefined) {
            trigger.backfill = {
                start: undefined
            }
        }

        if (flowVar.triggers === undefined) {
            flowVar.triggers = []
        }

        flowVar.triggers.push(trigger)

        flow.value = {...flowVar}
    }


    const isFlow = computed(() => {
        const currentTab = useEditorStore().current;
        return currentTab?.flow !== undefined || isCreating.value;
    })
    const isAllowedEdit = computed((): boolean => {
        if (!flow.value || !store.getters["auth/user"]) {
            return false;
        }

        return store.getters["auth/user"].isAllowed(
            permission.FLOW,
            action.UPDATE,
            flow.value?.namespace,
        );
    })

    const readOnlySystemLabel = computed(() => {
        if (!flow.value || !flow.value.labels) {
            return false;
        }

        return (flow.value.labels?.["system.readOnly"] === "true") || (flow.value.labels?.["system.readOnly"] === true);
    })

    const isReadOnly = computed(() => {
        return flow.value?.deleted || !isAllowedEdit.value || readOnlySystemLabel.value;
    })

    const baseOutdatedTranslationKey = computed(() => {
        const createOrUpdateKey = isCreating.value ? "create" : "update";
        return "outdated revision save confirmation." + createOrUpdateKey;
    })

    const flowErrors = computed((): string[] | undefined => {
        if (isFlow.value) {
            const flowExistsError =
                flowValidation.value?.outdated && isCreating.value
                    ? [`>>>>${baseOutdatedTranslationKey.value}`] // because translating is impossible here
                    : [];

            const constraintsError =
                flowValidation.value?.constraints?.split(/, ?/) ?? [];

            const errors = [...flowExistsError, ...constraintsError];

            return errors.length === 0 ? undefined : errors;
        }

        return undefined;
    })

    const flowInfos = computed(() => {
        if (isFlow.value) {
            const infos = flowValidation.value?.infos ?? [];

            return infos.length === 0 ? undefined : infos;
        }

        return undefined;
    })

    const flowHaveTasks = computed((): boolean => {
        if (isFlow.value) {
            const flowVar = isCreating.value ? flow.value?.source : flowYaml.value;
            return flowVar ? YAML_UTILS.flowHaveTasks(flowVar) : false;
        } else return false;
    })

    const nextRevision = computed((): number => {
        return (flow.value?.revision ?? 0) + 1;
    })

    const yamlWithNextRevision = computed((): string => {
        if (!flowYaml.value) return "";
        return `revision: ${nextRevision.value}\n${flowYaml.value}`;
    })

    const flowParsed = computed(() => {
        try {
            return YAML_UTILS.parse(flowYaml.value)
        } catch {
            return undefined
        }
    })
    const flowYamlMetadata = computed(() => {
        return YAML_UTILS.getMetadata(flowYaml.value ?? "");
    })

    return {
        isFlow,
        isAllowedEdit,
        readOnlySystemLabel,
        isReadOnly,
        baseOutdatedTranslationKey,
        flowErrors,
        flowInfos,
        flowHaveTasks,
        nextRevision,
        yamlWithNextRevision,
        flowParsed,
        flowYamlMetadata,
        flows,
        flow,
        task,
        search,
        total,
        overallTotal,
        flowGraph,
        invalidGraph,
        revisions,
        flowValidation,
        taskError,
        metrics,
        aggregatedMetrics,
        tasksWithMetrics,
        executeFlow,
        lastSaveFlow,
        isCreating,
        flowYaml,
        flowYamlOrigin,
        flowYamlBeforeAdd,
        confirmOutdatedSaveDialog,
        haveChange,
        expandedSubflows,
        metadata,
        addTrigger,
        setTrigger,
        removeTrigger,
        setExecuteFlow,
        onSaveMetadata,
        saveAll,
        save,
        onEdit,
        initYamlSource,
        findFlows,
        searchFlows,
        flowsByNamespace,
        loadFlow,
        loadTask,
        saveFlow,
        updateFlowTask,
        createFlow,
        loadDependencies,
        deleteFlowAndDependencies,
        deleteFlow,
        loadGraph,
        loadGraphFromSource,
        getGraphFromSourceResponse,
        loadRevisions,
        exportFlowByIds,
        exportFlowByQuery,
        importFlows,
        disableFlowByIds,
        disableFlowByQuery,
        enableFlowByIds,
        enableFlowByQuery,
        deleteFlowByIds,
        deleteFlowByQuery,
        validateFlow,
        validateTask,
        loadFlowMetrics,
        loadTaskMetrics,
        loadFlowAggregatedMetrics,
        loadTaskAggregatedMetrics,
        loadTasksWithMetrics,
    }
})
