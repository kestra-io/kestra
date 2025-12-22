import {computed, h, ref, watch} from "vue";
import {ElMessageBox} from "element-plus";
import permission from "../models/permission";
import action from "../models/action";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import Utils from "../utils/utils";
import {apiUrl} from "override/utils/route";
import {useCoreStore} from "./core";
import {useUnsavedChangesStore} from "./unsavedChanges";
import {defineStore} from "pinia";
import {FlowGraph} from "@kestra-io/ui-libs/vue-flow-utils";
import {makeToast} from "../utils/toast";
import {InputType} from "../utils/inputs";
import {globalI18n} from "../translations/i18n";
import {transformResponse} from "../components/dependencies/composables/useDependencies";
import {useAuthStore} from "override/stores/auth";
import {useRoute} from "vue-router";
import {useAxios} from "../utils/axios";
import {defaultNamespace} from "../composables/useNamespaces";

const textYamlHeader = {
    headers: {
        "Content-Type": "application/x-yaml"
    }
}

const VALIDATE = {validateStatus: (status: number) => status === 200 || status === 401};

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

export interface Input {
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

export interface Flow {
    id: string;
    namespace: string;
    source: string;
    revision?: number;
    deleted?: boolean;
    disabled?: boolean;
    labels?: Record<string, string | boolean>;
    triggers?: Trigger[];
    inputs?: Input[];
    errors?: { message: string; code?: string, id?: string }[];
    concurrency?: {
        limit: number;
        behavior: string;
    };
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
    const openAiCopilot = ref<boolean>(false)
    const lastSaveFlow = ref<string>()
    const isCreating = ref<boolean>(false)
    const flowYaml = ref<string>("")
    const flowYamlOrigin = ref<string>("")
    const confirmOutdatedSaveDialog = ref<boolean>(false)
    const expandedSubflows = ref<string[]>([])
    const metadata = ref<Record<string, any>>()
    const creationId = ref<string>();

    const axios = useAxios();

    const coreStore = useCoreStore();
    const unsavedChangesStore = useUnsavedChangesStore();

    const t = (key: string, values?: Record<string, any>) => {
        if (!globalI18n.value) {
            return key;
        }
        return (values ? globalI18n.value?.t(key, values) : globalI18n.value?.t(key)) ?? key;
    };

    function onSaveMetadata() {
        flowYaml.value = YAML_UTILS.updateMetadata(flowYaml.value ?? "", metadata.value ?? {});
        metadata.value = undefined;
    }

    const haveChange = computed(() => flowYamlOrigin.value !== flowYaml.value);

    watch(haveChange, (newValue) => {
        unsavedChangesStore.unsavedChange = newValue;
    });

    async function saveAll() {
        if ((!haveChange.value && !isCreating.value) || flowErrors.value?.length) {
            return;
        }

        if (!flow.value) return;
        flowYamlOrigin.value = flowYaml.value;
        return saveWithoutRevisionGuard();
    }

    const route = useRoute();

    const getNamespace = () => {
        return route.query.namespace || defaultNamespace();
    }

    async function save() {
        if (flowErrors.value?.length) {
            return;
        }

        const source = flowYaml.value;

        if (source) {
            return onEdit({source}).then((validation: any) => {
                if (validation?.outdated && !isCreating.value) {
                    return "confirmOutdatedSaveDialog";
                }
                const res = saveWithoutRevisionGuard();
                flowYamlOrigin.value = source;

                return res
            });
        }
    }

    async function onEdit({source, topologyVisible}: {
        source: string,
        editorViewType?: string,
        topologyVisible?: boolean
    }) {
        const flowBeforeEdit = flow.value;
        const flowOnValidation = flowParsed.value;

        if (!source.trim()?.length) {
            flowValidation.value = {
                constraints: t("flow must not be empty")
            };
            return
        }
        if (!isCreating.value) {
            try{
                if (flowBeforeEdit &&
                        (flowOnValidation.id !== flowBeforeEdit.id ||
                            flowOnValidation.namespace !== flowBeforeEdit.namespace)) {

                    coreStore.message = {
                        variant: "error",
                        title: t("readonly property"),
                        message: t("namespace and id readonly"),
                    };
                    flowYaml.value = YAML_UTILS.replaceIdAndNamespace(
                        source,
                        flowBeforeEdit.id,
                        flowBeforeEdit.namespace
                    );
                }
            } catch{
                // yaml is not always valid
            }
        }

        return validateFlow({
            flow: (isCreating.value ? flowYaml.value : yamlWithNextRevision.value) ?? ""
        })
            .then((value: {constraints?: string}) => {
                if (
                    topologyVisible &&
                    flowHaveTasks.value &&
                    // avoid sending empty errors
                    // they make the backend fail
                    flowBeforeEdit && (!flowBeforeEdit.errors || flowBeforeEdit.errors.every(e => typeof e.id === "string"))
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
                    isCreating.value = false;
                });
        } else {
            await saveFlow({flow: flowSource})
                .then((response: Flow) => {
                    toast.saved(response.id);
                });
        }

        if (isCreatingBackup || overrideFlow) {
            return "redirect_to_update";
        }

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

    async function initYamlSource() {
        if (!flow.value) return;
        const {source} = flow.value;
        flowYaml.value = source;
        flowYamlOrigin.value = source;
        if (flowHaveTasks.value) {
            fetchGraph();
        }

        // validate flow on first load
        return validateFlow({flow: isCreating.value ? source : yamlWithNextRevision.value})
    }

    function findFlows(options: { [key: string]: any }) {
        const sortString = options.sort ? `?sort=${options.sort}` : ""
        delete options.sort
        return axios.get(`${apiUrl()}/flows/search${sortString}`, {
            params: options
        }).then(response => {
            if (options.onlyTotal) {
                return response.data.total;
            }

            else {
                flows.value = response.data.results
                total.value = response.data.total
                overallTotal.value = response.data.results.filter((f: any) => f.namespace !== "tutorial").length

                return response.data;
            }
        })
    }
    function searchFlows(options: { [key: string]: any }) {
        const sortString = options.sort ? `?sort=${options.sort}` : ""
        delete options.sort
        return axios.get(`${apiUrl()}/flows/source${sortString}`, {
            params: options
        }).then(response => {
            search.value = response.data.results
            total.value = response.data.total

            return response.data;
        })
    }

    function flowsByNamespace(namespace: string) {
        return axios.get(`${apiUrl()}/flows/${namespace}`).then(response => {
            return response.data;
        })
    }

    function loadFlow(options: { namespace: string, id: string, revision?: string, allowDeleted?: boolean, source?: boolean, store?: boolean, deleted?: boolean, httpClient?: any }) {
        const httpClient = options.httpClient ?? axios
        return httpClient.get(`${apiUrl()}/flows/${options.namespace}/${options.id}`,
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
                overallTotal.value = 1;

                return response.data;
            })
    }
    function loadTask(options: { namespace: string, id: string, taskId: string, revision?: string }) {
        return axios.get(
            `${apiUrl()}/flows/${options.namespace}/${options.id}/tasks/${options.taskId}${options.revision ? "?revision=" + options.revision : ""}`,
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
        return axios.put(`${apiUrl()}/flows/${flowData.namespace}/${flowData.id}`, options.flow, {
            ...textYamlHeader,
            ...VALIDATE
        })
            .then(response => {
                if (response.status >= 300) {
                    return Promise.reject(response)
                } else {
                    flow.value = response.data;

                    return response.data;
                }
            })
    }
    function updateFlowTask(options: { flow: Flow, task: Task }) {
        return axios
            .patch(`${apiUrl()}/flows/${options.flow.namespace}/${options.flow.id}/${options.task.id}`, options.task).then(response => {
                flow.value = response.data;

                return response.data;
            })
            .then(flow => {
                loadGraph({flow});

                return flow;
            })
    }

    function createFlow(options: { flow: string }) {
        return axios.post(`${apiUrl()}/flows`, options.flow, {
            ...textYamlHeader,
            ...VALIDATE
        }).then(response => {
            if (response.status >= 300) {
                return Promise.reject(response)
            }

            const creationPanels = localStorage.getItem(`el-fl-creation-${creationId.value}`) ?? YAML_UTILS.stringify([]);
            localStorage.setItem(`el-fl-${flow.value!.namespace}-${flow.value!.id}`, creationPanels);

            flow.value = response.data;

            // clean-up
            localStorage.removeItem(`el-fl-creation-${creationId.value}`);
            creationId.value = undefined;

            return response.data;
        })
    }

    function loadDependencies(options: { namespace: string, id: string, subtype: "FLOW" | "EXECUTION" }, onlyCount = false) {
        return axios.get(`${apiUrl()}/flows/${options.namespace}/${options.id}/dependencies?expandAll=${onlyCount ? false : true}`).then(response => {
            return {
                ...(!onlyCount ? {data: transformResponse(response.data, options.subtype)} : {}),
                count: response.data.nodes ? new Set(response.data.nodes.map((r:{uid:string}) => r.uid)).size : 0
            };
        })
    }

function deleteFlowAndDependencies() {
    const metadata = flowYamlMetadata.value;

    return axios
        .get(
            `${apiUrl()}/flows/${metadata.namespace}/${metadata.id}/dependencies`,
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
        return axios.delete(`${apiUrl()}/flows/${options.namespace}/${options.id}`).then(() => {
            flow.value = undefined;
        })
    }

    function loadGraph(options: { flow: Flow, params?: any }) {
        const flowVar = options.flow;
        const params = options.params ? options.params : {};
        if (flowVar.revision) {
            params["revision"] = flowVar.revision;
        }
        return axios.get(`${apiUrl()}/flows/${flowVar.namespace}/${flowVar.id}/graph`, {params}).then(response => {
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
        return axios.post(`${apiUrl()}/flows/graph`, flowSource, {...config, withCredentials: true})
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
                    coreStore.message = {
                        title: "Couldn't expand subflow",
                        message: error.response.data.message,
                        variant: "error"
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
        return axios.post(`${apiUrl()}/flows/graph`, flowSource, {...config})
            .then(response => response.data)
    }

    function loadRevisions(options: { namespace: string, id: string, store?: boolean }) {
        return axios.get(`${apiUrl()}/flows/${options.namespace}/${options.id}/revisions`).then(response => {
            if (options.store !== false) {
                revisions.value = response.data
            }
            return response.data;
        })
    }

    function exportFlowByIds(options: { ids: string[] }) {
        return axios.post(`${apiUrl()}/flows/export/by-ids`, options.ids, {responseType: "blob"})
            .then(response => {
                const blob = new Blob([response.data], {type: "application/octet-stream"});
                const url = window.URL.createObjectURL(blob)
                Utils.downloadUrl(url, "flows.zip");
            });
    }

    function exportFlowByQuery(options: { namespace: string, id: string }) {
        return axios.get(`${apiUrl()}/flows/export/by-query`, {params: options, headers: {"Accept": "application/octet-stream"}})
            .then(response => {
                Utils.downloadUrl(response.request.responseURL, "flows.zip");
            });
    }

    async function exportFlowAsCSV(params: any) {
        const response = await axios.get(
            `${apiUrl()}/flows/export/by-query/csv`,
            {params, responseType: "blob"}
        );
        const url = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement("a");
        link.href = url;
        link.setAttribute("download", "flows.csv");
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
    }

    function importFlows(options: { file: File, namespace: string, override?: boolean }) {
        return axios.post(`${apiUrl()}/flows/import`, Utils.toFormData(options), {
            headers: {"Content-Type": "multipart/form-data"}
        }).then(response => {
            return response;
        });
    }
    function disableFlowByIds(options: { ids: {id: string, namespace: string}[] }) {
        return axios.post(`${apiUrl()}/flows/disable/by-ids`, options.ids)
    }
    function disableFlowByQuery(options: { namespace: string, id: string }) {
        return axios.post(`${apiUrl()}/flows/disable/by-query`, options, {params: options})
    }
    function enableFlowByIds(options: { ids: {id: string, namespace: string}[] }) {
        return axios.post(`${apiUrl()}/flows/enable/by-ids`, options.ids)
    }
    function enableFlowByQuery(options: { namespace: string, id: string }) {
        return axios.post(`${apiUrl()}/flows/enable/by-query`, options, {params: options})
    }

    function deleteFlowByIds(options: { ids: {id: string, namespace: string}[] }) {
        return axios.delete(`${apiUrl()}/flows/delete/by-ids`, {data: options.ids})
    }

    function deleteFlowByQuery(options: { namespace: string, id: string }) {
        return axios.delete(`${apiUrl()}/flows/delete/by-query`, {params: options})
    }

    function validateFlow(options: { flow: string }) {
        const flowValidationIssues: FlowValidations = {};
        if(isCreating.value) {
            const {namespace} = YAML_UTILS.getMetadata(options.flow);
            if(authStore.user && !authStore.user.isAllowed(
                permission.FLOW,
                action.CREATE,
                namespace,
            )) {
                flowValidationIssues.constraints = t("flow creation denied in namespace", {namespace});
            }
        }
        return axios.post(`${apiUrl()}/flows/validate`, options.flow, {...textYamlHeader, withCredentials: true})
            .then(response => {
                const constraintsArray = [response?.data[0]?.constraints, flowValidationIssues.constraints].filter(Boolean)
                flowValidation.value = constraintsArray.length === 0 ? {} : {
                    constraints: constraintsArray.join(", ")
                };
                return flowValidation.value
            })
    }

    function validateTask(options: { task: string, section: string }) {
        return axios.post(`${apiUrl()}/flows/validate/task`, options.task, {...textYamlHeader, withCredentials: true, params: {section: options.section}})
            .then(response => {
                taskError.value = response.data.constraints;
                return response.data
            })
    }
    function loadFlowMetrics(options: { namespace: string, id: string }) {
        return axios.get(`${apiUrl()}/metrics/names/${options.namespace}/${options.id}`)
            .then(response => {
                metrics.value = response.data
                return response.data
            })
    }
    function loadTaskMetrics(options: { namespace: string, id: string, taskId: string }) {
        return axios.get(`${apiUrl()}/metrics/names/${options.namespace}/${options.id}/${options.taskId}`)
            .then(response => {
                metrics.value = response.data
                return response.data
            })
    }
    function loadTasksWithMetrics(options: { namespace: string, id: string }) {
        return axios.get(`${apiUrl()}/metrics/tasks/${options.namespace}/${options.id}`)
            .then(response => {
                tasksWithMetrics.value = response.data
                return response.data
            })
    }
    function loadFlowAggregatedMetrics(options: { namespace: string, id: string, metric: string }) {
        return axios.get(`${apiUrl()}/metrics/aggregates/${options.namespace}/${options.id}/${options.metric}`, {params: options})
            .then(response => {
                aggregatedMetrics.value = response.data
                return response.data
            })
    }
    function loadTaskAggregatedMetrics(options: { namespace: string, id: string, taskId: string, metric: string }) {
        return axios.get(`${apiUrl()}/metrics/aggregates/${options.namespace}/${options.id}/${options.taskId}/${options.metric}`, {params: options})
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

    function setOpenAiCopilot(value: boolean) {
        openAiCopilot.value = value;
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

    const authStore = useAuthStore()

    const isAllowedEdit = computed((): boolean => {
        if (!flow.value || !authStore.user) {
            return false;
        }

        return (isCreating.value && authStore.user.hasAnyAction(permission.FLOW, action.UPDATE))
         || authStore.user.isAllowed(
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
        const key = baseOutdatedTranslationKey.value;
        const flowExistsError =
            flowValidation.value?.outdated && isCreating.value
                ? [`${t(key + ".description")} ${t(key + ".details")}`]
                : [];

        const constraintsError =
            flowValidation.value?.constraints?.split(/, ?/) ?? [];

        const errors = [...flowExistsError, ...constraintsError];

        return errors.length === 0 ? undefined : errors;
    })

    const flowInfos = computed(() => {
        const infos = flowValidation.value?.infos ?? [];

        return infos.length === 0 ? undefined : infos;
    })

    const flowHaveTasks = computed((): boolean => {
        const flowVar = isCreating.value ? flow.value?.source : flowYaml.value;
        return flowVar ? YAML_UTILS.flowHaveTasks(flowVar) : false;
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
        creationId,
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
        openAiCopilot,
        lastSaveFlow,
        isCreating,
        flowYaml,
        flowYamlOrigin,
        confirmOutdatedSaveDialog,
        haveChange,
        expandedSubflows,
        metadata,
        addTrigger,
        setTrigger,
        removeTrigger,
        setExecuteFlow,
        setOpenAiCopilot,
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
        exportFlowAsCSV,
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
        getNamespace,
    }
})
