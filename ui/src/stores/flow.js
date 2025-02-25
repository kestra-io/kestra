import axios from "axios";
import {h} from "vue";
import {ElMessageBox} from "element-plus";
import permission from "../models/permission";
import action from "../models/action";
import YamlUtils from "../utils/yamlUtils";
import Utils from "../utils/utils";
import {editorViewTypes} from "../../utils/constants";
import {apiUrl} from "override/utils/route";

const textYamlHeader = {
    headers: {
        "Content-Type": "application/x-yaml"
    }
}
export default {
    namespaced: true,
    state: {
        flows: undefined,
        flow: undefined,
        task: undefined,
        search: undefined,
        total: 0,
        overallTotal: undefined,
        flowGraph: undefined,
        flowGraphParam: undefined,
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
        confirmOutdatedSaveDialog: false,
        haveChange: false,
    },

    actions: {
        onEdit({getters, dispatch, commit, state, rootDispatch, rootCommit, rootState}, {flowYaml, currentIsFlow, id, namespace, editorViewType, viewType}) {
            commit("setFlowYaml", flowYaml);
            const flowParsed = YamlUtils.parse(flowYaml);
            const currentTab =rootState.editor.current;

            if (currentIsFlow) {
                if (
                    flowParsed &&
                    !state.isCreating &&
                    (id !== flowParsed.id ||
                        namespace !== flowParsed.namespace)
                ) {
                    rootDispatch("core/showMessage", {
                        variant: "error",
                        title: this.$i18n.t("readonly property"),
                        message: this.$i18n.t("namespace and id readonly"),
                    });
                    flowYaml.value = YamlUtils.replaceIdAndNamespace(
                        flowYaml.value,
                        id,
                        namespace
                    );
                    return;
                }
            }

            commit("setHaveChange", true);
            if(editorViewType.value === "YAML") {
                rootDispatch("core/isUnsaved", true);
            }

            if(!state.isCreating){
                rootCommit("editor/changeOpenedTabs", {
                    action: "dirty",
                    ...currentTab,
                    name: currentTab?.name ?? "Flow",
                    path: currentTab?.path ?? "Flow.yaml",
                    dirty: true
                });
            }

            if(!currentIsFlow) return;

            return dispatch("validateFlow", {flow: state.isCreating ? flowYaml.value : getters.yamlWithNextRevision})
                .then((value) => {
                    if (
                        getters.flowHaveTasks &&
                        [
                            editorViewTypes.TOPOLOGY,
                            editorViewTypes.SOURCE_TOPOLOGY,
                        ].includes(viewType)
                    ) {
                        if(!value.constraints) dispatch("fetchGraph");
                    }

                    return value;
                });
        },
        async saveWithoutRevisionGuard ({commit, state, dispatch, getters, rootDispatch}) {
            const flowYaml = state.flowYaml;
            const flowParsed = YamlUtils.parse(flowYaml);

            if (flowParsed === undefined) {
                rootDispatch("core/showMessage", {
                    variant: "error",
                    title: this.$t("invalid flow"),
                    message: this.$t("invalid yaml"),
                });

                return;
            }
            let overrideFlow = false;
            if (getters.flowErrors) {
                if (state.flowValidation.outdated && state.isCreating) {
                    overrideFlow = await ElMessageBox({
                        title: this.$t("override.title"),
                        message: () => {
                            return h("div", null, [
                                h("p", null, this.$t("override.details")),
                            ]);
                        },
                        showCancelButton: true,
                        confirmButtonText: this.$t("ok"),
                        cancelButtonText: this.$t("cancel"),
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

            if (state.isCreating && !overrideFlow) {
                await dispatch("createFlow", {flow: flowYaml.value})
                    .then((response) => {
                        this.toast.saved(response.id);
                        rootDispatch("core/isUnsaved", false);
                    });
            } else {
                await dispatch("saveFlow", {flow: flowYaml.value})
                    .then((response) => {
                        this.toast.saved(response.id);
                        rootDispatch("core/isUnsaved", false);
                    });
            }

            if (state.isCreating || overrideFlow) {
                return "redirect_to_update";
            }

            commit("setHaveChange", false);
            await dispatch("validateFlow", {
                flow: state.isCreating ? flowYaml.value : getters.yamlWithNextRevision
            });
        },
        fetchGraph({getters, state, dispatch}) {
            return dispatch("loadGraphFromSource", {
                flow: state.flowYaml,
                config: {
                    params: {
                        // due to usage of axios instance instead of $http which doesn't convert arrays
                        subflows: getters.expandedSubflows.join(","),
                    },
                    validateStatus: (status) => {
                        return status === 200;
                    },
                },
            });
        },
        async initYamlSource({getters, commit, dispatch, state}, {viewType}) {
            const {source} = getters.flow;
            commit("setFlowYaml", source);
            commit("setFlowYamlOrigin", source);
            if (getters.flowHaveTasks) {
                if (
                    [
                        editorViewTypes.TOPOLOGY,
                        editorViewTypes.SOURCE_TOPOLOGY,
                    ].includes(viewType)
                ) {
                    await dispatch("fetchGraph");
                } else {
                    dispatch("fetchGraph");
                }
            }

            // validate flow on first load
            return dispatch("validateFlow", {flow: state.isCreating ? source : getters.yamlWithNextRevision})
        },
        findFlows({commit}, options) {
            const sortString = options.sort ? `?sort=${options.sort}` : ""
            delete options.sort
            return this.$http.get(`${apiUrl(this)}/flows/search${sortString}`, {
                params: options
            }).then(response => {
                commit("setFlows", response.data.results)
                commit("setTotal", response.data.total)
                commit("setOverallTotal", response.data.results.filter(f => f.namespace !== "tutorial").length)

                return response.data;
            })
        },
        searchFlows({commit}, options) {
            const sortString = options.sort ? `?sort=${options.sort}` : ""
            delete options.sort
            return this.$http.get(`${apiUrl(this)}/flows/source${sortString}`, {
                params: options
            }).then(response => {
                commit("setSearch", response.data.results)
                commit("setTotal", response.data.total)

                return response.data;
            })
        },
        flowsByNamespace(_, namespace) {
            return this.$http.get(`${apiUrl(this)}/flows/${namespace}`).then(response => {
                return response.data;
            })
        },
        loadFlow({commit}, options) {
            const httpClient = options.httpClient ?? this.$http
            return httpClient.get(`${apiUrl(this)}/flows/${options.namespace}/${options.id}`,
                {
                    params: {
                        revision: options.revision,
                        allowDeleted: options.allowDeleted,
                        source: options.source === undefined ? true : undefined
                    },
                    validateStatus: (status) => {
                        return options.deleted ? status === 200 || status === 404 : status === 200;
                    }
                })
                .then(response => {
                    if (response.data.exception) {
                        commit("core/setMessage", {
                            title: "Invalid source code",
                            message: response.data.exception,
                            variant: "danger"
                        }, {root: true});
                        delete response.data.exception;
                    }
                    if(options.store === false) {
                        return response.data;
                    }
                    commit("setOverallTotal", 1)
                    return response.data;
                })
        },
        loadTask({commit}, options) {
            return this.$http.get(
                `${apiUrl(this)}/flows/${options.namespace}/${options.id}/tasks/${options.taskId}${options.revision ? "?revision=" + options.revision : ""}`,
                {
                    validateStatus: (status) => {
                        return status === 200 || status === 404;
                    }
                }
            )
                .then(response => {
                    if (response.status === 200) {
                        commit("setTask", response.data)

                        return response.data;
                    } else {
                        return null;
                    }
                })
        },
        saveFlow({commit, _dispatch}, options) {
            const flowData = YamlUtils.parse(options.flow)
            return this.$http.put(`${apiUrl(this)}/flows/${flowData.namespace}/${flowData.id}`, options.flow, textYamlHeader)
                .then(response => {
                    if (response.status >= 300) {
                        return Promise.reject(new Error("Server error on flow save"))
                    } else {
                        commit("setFlow", response.data);

                        return response.data;
                    }
                })
        },
        updateFlowTask({commit, dispatch}, options) {
            return this.$http
                .patch(`${apiUrl(this)}/flows/${options.flow.namespace}/${options.flow.id}/${options.task.id}`, options.task).then(response => {
                    commit("setFlow", response.data)

                    return response.data;
                })
                .then(flow => {
                    dispatch("loadGraph", {flow});

                    return flow;
                })
        },
        createFlow({commit}, options) {
            return this.$http.post(`${apiUrl(this)}/flows`, options.flow, textYamlHeader).then(response => {
                commit("setFlow", response.data);

                return response.data;
            })
        },
        deleteFlow({commit}, flow) {
            return this.$http.delete(`${apiUrl(this)}/flows/${flow.namespace}/${flow.id}`).then(() => {
                commit("setFlow", null)
            })
        },
        loadGraph({commit}, options) {
            const flow = options.flow;
            const params = options.params ? options.params : {};
            if (flow.revision) {
                params["revision"] = flow.revision;
            }
            return this.$http.get(`${apiUrl(this)}/flows/${flow.namespace}/${flow.id}/graph`, {params}).then(response => {
                commit("setFlowGraph", response.data)
                commit("setFlowGraphParam", {
                    namespace: flow.namespace,
                    id: flow.id,
                    revision: flow.revision
                })

                return response.data;
            })
        },
        loadGraphFromSource({commit, state}, options) {
            const config = options.config ? {...options.config, ...textYamlHeader} : textYamlHeader;
            const flowParsed = YamlUtils.parse(options.flow);
            let flowSource = options.flow
            if (!flowParsed.id || !flowParsed.namespace) {
                flowSource = YamlUtils.updateMetadata(flowSource, {id: "default", namespace: "default"})
            }
            return axios.post(`${apiUrl(this)}/flows/graph`, flowSource, {...config, withCredentials: true})
                .then(response => {
                    commit("setFlowGraph", response.data)

                    let flow = YamlUtils.parse(options.flow);
                    flow.id = state.flow?.id ?? flow.id;
                    flow.namespace = state.flow?.namespace ?? flow.namespace;
                    flow.source = options.flow;
                    // prevent losing revision when loading graph from source
                    flow.revision = state.flow?.revision;
                    commit("setFlow", flow);
                    commit("setFlowGraphParam", {
                        namespace: flow.namespace ? flow.namespace : "default",
                        id: flow.id ? flow.id : "default",
                        revision: flow.revision
                    })

                    return response;
                }).catch(error => {
                    if (error.response?.status === 422 && (!config?.params?.subflows || config?.params?.subflows?.length === 0)) {
                        return Promise.resolve(error.response);
                    }

                    if([404, 422].includes(error.response?.status) && config?.params?.subflows?.length > 0) {
                        commit("core/setMessage", {
                            title: "Couldn't expand subflow",
                            message: error.response.data.message,
                            variant: "danger"
                        }, {root: true});
                    }

                    return Promise.reject(error);
                })
        },
        getGraphFromSourceResponse({_commit}, options) {
            const config = options.config ? {...options.config, ...textYamlHeader} : textYamlHeader;
            const flowParsed = YamlUtils.parse(options.flow);
            let flowSource = options.flow
            if (!flowParsed.id || !flowParsed.namespace) {
                flowSource = YamlUtils.updateMetadata(flowSource, {id: "default", namespace: "default"})
            }
            return this.$http.post(`${apiUrl(this)}/flows/graph`, flowSource, {...config})
                .then(response => response.data)
        },
        loadRevisions({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/flows/${options.namespace}/${options.id}/revisions`).then(response => {
                commit("setRevisions", response.data)
                return response.data;
            })
        },
        exportFlowByIds(_, options) {
            return this.$http.post(`${apiUrl(this)}/flows/export/by-ids`, options.ids, {responseType: "blob"})
                .then(response => {
                    const blob = new Blob([response.data], {type: "application/octet-stream"});
                    const url = window.URL.createObjectURL(blob)
                    Utils.downloadUrl(url, "flows.zip");
                });
        },
        exportFlowByQuery(_, options) {
            return this.$http.get(`${apiUrl(this)}/flows/export/by-query`, {params: options, headers: {"Accept": "application/octet-stream"}})
                .then(response => {
                    Utils.downloadUrl(response.request.responseURL, "flows.zip");
                });
        },
        importFlows(_, options) {
            return this.$http.post(`${apiUrl(this)}/flows/import`, Utils.toFormData(options), {headers: {"Content-Type": "multipart/form-data"}})
        },
        disableFlowByIds(_, options) {
            return this.$http.post(`${apiUrl(this)}/flows/disable/by-ids`, options.ids)
        },
        disableFlowByQuery(_, options) {
            return this.$http.post(`${apiUrl(this)}/flows/disable/by-query`, options, {params: options})
        },
        enableFlowByIds(_, options) {
            return this.$http.post(`${apiUrl(this)}/flows/enable/by-ids`, options.ids)
        },
        enableFlowByQuery(_, options) {
            return this.$http.post(`${apiUrl(this)}/flows/enable/by-query`, options, {params: options})
        },
        deleteFlowByIds(_, options) {
            return this.$http.delete(`${apiUrl(this)}/flows/delete/by-ids`, {data: options.ids})
        },
        deleteFlowByQuery(_, options) {
            return this.$http.delete(`${apiUrl(this)}/flows/delete/by-query`, {params: options})
        },
        validateFlow({commit}, options) {
            return axios.post(`${apiUrl(this)}/flows/validate`, options.flow, {...textYamlHeader, withCredentials: true})
                .then(response => {
                    commit("setFlowValidation", response.data[0])
                    return response.data[0]
                })
        },
        validateTask({commit}, options) {
            return axios.post(`${apiUrl(this)}/flows/validate/task`, options.task, {...textYamlHeader, withCredentials: true, params: {section: options.section}})
                .then(response => {
                    commit("setTaskError", response.data.constraints)
                    return response.data
                })
        },
        loadFlowMetrics({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/metrics/names/${options.namespace}/${options.id}`)
                .then(response => {
                    commit("setMetrics", response.data)
                    return response.data
                })
        },
        loadTaskMetrics({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/metrics/names/${options.namespace}/${options.id}/${options.taskId}`)
                .then(response => {
                    commit("setMetrics", response.data)
                    return response.data
                })
        },
        loadTasksWithMetrics({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/metrics/tasks/${options.namespace}/${options.id}`)
                .then(response => {
                    commit("setTasksWithMetrics", response.data)
                    return response.data
                })
        },
        loadFlowAggregatedMetrics({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/metrics/aggregates/${options.namespace}/${options.id}/${options.metric}`, {params: options})
                .then(response => {
                    commit("setAggregatedMetric", response.data)
                    return response.data
                })
        },
        loadTaskAggregatedMetrics({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/metrics/aggregates/${options.namespace}/${options.id}/${options.taskId}/${options.metric}`, {params: options})
                .then(response => {
                    commit("setAggregatedMetric", response.data)
                    return response.data
                })
        },
    },
    mutations: {
        setFlows(state, flows) {
            state.flows = flows
        },
        setSearch(state, search) {
            state.search = search
        },
        setRevisions(state, revisions) {
            state.revisions = revisions
        },
        setFlow(state, flow) {
            state.flow = flow;
            state.lastSaveFlow = flow;
            // if (state.flowGraph !== undefined && state.flowGraphParam && flow) {
            //     if (state.flowGraphParam.namespace !== flow.namespace || state.flowGraphParam.id !== flow.id) {
            //         state.flowGraph = undefined
            //     }
            // }

        },
        setFlowGraphParam(state, flow) {
            state.flowGraphParam = flow
        },
        setTask(state, task) {
            state.task = task;
        },
        setTrigger(state, {index, trigger}) {
            let flow = state.flow;

            if (flow.triggers === undefined) {
                flow.triggers = []
            }

            flow.triggers[index] = trigger;

            state.flow = {...flow}
        },
        removeTrigger(state, index) {
            let flow = state.flow;
            flow.triggers.splice(index, 1);

            state.flow = {...flow}
        },
        executeFlow(state, value) {
            state.executeFlow = value;
        },
        addTrigger(state, trigger) {
            let flow = state.flow;

            if (trigger.backfill === undefined) {
                trigger.backfill = {
                    start: undefined
                }
            }

            if (flow.triggers === undefined) {
                flow.triggers = []
            }

            flow.triggers.push(trigger)

            state.flow = {...flow}
        },
        setTotal(state, total) {
            state.total = total
        },
        setOverallTotal(state, total) {
            state.overallTotal = total
        },
        setFlowGraph(state, flowGraph) {
            state.flowGraph = flowGraph
        },
        setFlowValidation(state, flowValidation) {
            state.flowValidation = flowValidation
        },
        setTaskError(state, taskError) {
            state.taskError = taskError
        },
        setMetrics(state, metrics) {
            state.metrics = metrics
        },
        setAggregatedMetric(state, aggregatedMetric) {
            state.aggregatedMetric = aggregatedMetric
        },
        setTasksWithMetrics(state, tasksWithMetrics) {
            state.tasksWithMetrics = tasksWithMetrics
        },
        setFlowYaml(state, flowYaml) {
            state.flowYaml = flowYaml
        },
        setCreating(state, value) {
            state.isCreating = value
        },
        setFlowYamlOrigin(state, value) {
            state.flowYamlOrigin = value
        },
        setHaveChange(state, value) {
            state.haveChange = value
        }
    },
    getters: {
        isFlow(state) {
            return state.flow !== undefined || state.isCreating;
        },
        lastSaveFlow(state){
            if(state.lastSavedFlow){
                return state.lastSavedFlow;
            }
        },
        flow(state) {
            if (state.flow) {
                return state.flow;
            }
        },
        flowValidation(state) {
            if (state.flowValidation) {
                return state.flowValidation;
            }
        },
        taskError(state) {
            if (state.taskError) {
                return state.taskError;
            }
        },
        isAllowedEdit(_state, getters, _rootState, rootGetters) {
            if (!getters.flow || !rootGetters["auth/user"]) {
                return false;
            }

            return rootGetters["auth/user"].isAllowed(
                permission.FLOW,
                action.UPDATE,
                getters.flow.namespace,
            );
        },
        readOnlySystemLabel(_state, getters) {
            if (!getters.flow) {
                return false;
            }

            return (getters.flow.labels?.["system.readOnly"] === "true") || (getters.flow.labels?.["system.readOnly"] === true);
        },
        baseOutdatedTranslationKey(state) {
                const createOrUpdateKey = state.isCreating ? "create" : "update";
                return "outdated revision save confirmation." + createOrUpdateKey;
        },
        outdatedMessage(_, getters){
            return `${this.$i18n.t(getters.baseOutdatedTranslationKey + ".description")} ${this.$i18n.t(
                getters.baseOutdatedTranslationKey + ".details"
            )}`;
        },
        flowErrors(state, getters){
            if (getters.isFlow) {
                const flowExistsError =
                    state.flowValidation?.outdated && state.isCreating
                        ? [getters.outdatedMessage]
                        : [];

                const constraintsError =
                    state.flowValidation?.constraints?.split(/, ?/) ?? [];

                const errors = [...flowExistsError, ...constraintsError];

                return errors.length === 0 ? undefined : errors;
            }

            return undefined;
        },
        flowWarnings(state, getters){
            if (getters.isFlow) {
                const outdatedWarning =
                    state.flowValidation?.outdated && !state.isCreating
                        ? [getters.outdatedMessage]
                        : [];

                const deprecationWarnings =
                    state.flowValidation?.deprecationPaths?.map(
                        (f) => `${f} ${this.$i18n.t("is deprecated")}.`
                    ) ?? [];

                const otherWarnings = state.flowValidation?.warnings ?? [];

                const warnings = [
                    ...outdatedWarning,
                    ...deprecationWarnings,
                    ...otherWarnings,
                ];

                return warnings.length === 0 ? undefined : warnings;
            }

            return undefined;
        },
        flowInfos(state, getters){
            if (getters.isFlow) {
                const infos = state.flowValidation?.infos ?? [];

                return infos.length === 0 ? undefined : infos;
            }

            return undefined;
        },
        flowHaveTasks(state, getters){
            if (getters.isFlow) {
                const flow = state.isCreating ? getters.flow.source : state.flowYaml;
                return flow ? YamlUtils.flowHaveTasks(flow) : false;
            } else return false;
        },
        nextRevision(_state, getters){
            return getters.flow.revision + 1;
        },
        yamlWithNextRevision(_state, getters){
            return `revision: ${getters.nextRevision}\n${getters.flow.source}`;
        }
    }
}
