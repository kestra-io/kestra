import {h} from "vue";
import {ElMessageBox} from "element-plus";
import permission from "../models/permission";
import action from "../models/action";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import Utils from "../utils/utils";
import {editorViewTypes} from "../utils/constants";
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
    },

    actions: {
        onSaveMetadata({commit, state}){
            commit("setFlowYaml", YAML_UTILS.updateMetadata(state.flowYaml, state.metadata));
            commit("setMetadata", null);
            commit("setHaveChange", true)
        },
        async saveAll({dispatch, state, commit, getters, rootState}){
            const hasAnyDirtyTabs = rootState.editor.tabs.some(t => t.dirty === true);
            const hasChanges = state.haveChange || hasAnyDirtyTabs;
            
            if (getters.flowErrors?.length || !hasChanges && !state.isCreating) {
                return;
            }

            await dispatch("editor/saveAllTabs", {namespace: getters.namespace}, {root: true});
            commit("setFlowYamlOrigin", state.flowYaml);
            return dispatch("saveWithoutRevisionGuard");
        },
        async save({getters, dispatch, commit, state, rootState}, {content, namespace}){
            const hasAnyDirtyTabs = rootState.editor.tabs.some(t => t.dirty === true);
            const hasChanges = state.haveChange || hasAnyDirtyTabs;
            
            if (getters.flowErrors?.length || !hasChanges && !state.isCreating) {
                return;
            }

            const source = state.flowYaml
            const currentTab = rootState.editor.current;

            if (getters.isFlow) {
                return dispatch("onEdit", {source, currentIsFlow:true}).then((validation) => {
                    if (validation?.outdated && !state.isCreating) {
                        return "confirmOutdatedSaveDialog";
                    }
                    const res = dispatch("saveWithoutRevisionGuard");
                    commit("setFlowYamlOrigin", source);

                    if (currentTab && currentTab.name) {
                        commit("editor/setTabDirty", {
                            name: "Flow",
                            path: "Flow.yaml",
                            dirty: false,
                            flow: true,
                        }, {root: true});
                    }
                    return res
                });
            } else {
                if(!currentTab?.dirty) return;

                await dispatch("namespace/createFile", {
                    namespace: namespace ?? getters.namespace,
                    path: currentTab.path ?? currentTab.name,
                    content,
                }, {root: true});
                commit("editor/setTabDirty", {
                    path: currentTab.path,
                    name: currentTab.name,
                    dirty: false
                }, {root: true});

                dispatch("core/isUnsaved", false, {root: true});
            }
        },
        onEdit({getters, dispatch, commit, state, rootState}, {source, currentIsFlow, editorViewType, topologyVisible}) {
            const flowParsed = getters.flowParsed;
            const currentTab = rootState.editor.current;

            if (currentIsFlow) {
                if (!source.trim()?.length) {
                    commit("setFlowValidation", {constraints: this.$i18n.t("flow must not be empty")})
                    return
                }
                if (!state.isCreating){
                    if(!source.trim()?.length ||
                        (flowParsed &&
                        (getters.id !== flowParsed.id ||
                            getters.namespace !== flowParsed.namespace)))
                        {
                        dispatch("core/showMessage", {
                            variant: "error",
                            title: this.$i18n.t("readonly property"),
                            message: this.$i18n.t("namespace and id readonly"),
                        }, {root: true});
                        commit("setFlowYaml", YAML_UTILS.replaceIdAndNamespace(
                            source,
                            getters.id,
                            getters.namespace
                        ));
                    }
                }
            }

            commit("setHaveChange", true);
            if(editorViewType === "YAML") {
                dispatch("core/isUnsaved", true, {root: true});
            }

            if(!state.isCreating){
                commit("editor/setTabDirty", {
                    ...currentTab,
                    name: currentTab?.name ?? "Flow",
                    path: currentTab?.path ?? "Flow.yaml",
                    dirty: true
                }, {root: true});
            }

            if(!currentIsFlow) return;

            return dispatch("validateFlow", {
                flow: state.isCreating ? state.flowYaml : getters.yamlWithNextRevision
            })
                .then((value) => {
                    if (
                        topologyVisible &&
                        getters.flowHaveTasks &&
                        // avoid sending empty errors
                        // they make the backend fail
                        flowParsed && (!flowParsed.errors || flowParsed.errors.every(e => typeof e.id === "string"))
                    ) {
                        if(!value.constraints) dispatch("fetchGraph");
                    }

                    return value;
                });
        },
        async saveWithoutRevisionGuard ({commit, state, dispatch, getters}) {
            const flowYaml = state.flowYaml;
            const flowParsed = getters.flowParsed;

            if (flowParsed === undefined) {
                dispatch("core/showMessage", {
                    variant: "error",
                    title: this.$i18n.t("invalid flow"),
                    message: this.$i18n.t("invalid yaml"),
                }, {root: true});

                return;
            }

            let overrideFlow = false;
            if (getters.flowErrors) {
                if (state.flowValidation.outdated && state.isCreating) {
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

            const {isCreating} = state;
            if (state.isCreating && !overrideFlow) {
                await dispatch("createFlow", {flow: flowYaml})
                    .then((response) => {
                        this.$toast.bind({$t: this.$i18n.t})().saved(response.id);
                        dispatch("core/isUnsaved", false, {root: true});
                        commit("setIsCreating", false);
                        commit("setHaveChange", false);
                    });
            } else {
                await dispatch("saveFlow", {flow: flowYaml})
                    .then((response) => {
                        this.$toast.bind({$t: this.$i18n.t})().saved(response.id);
                        dispatch("core/isUnsaved", false, {root: true});
                    });
            }

            if (isCreating || overrideFlow) {
                return "redirect_to_update";
            }

            commit("setHaveChange", false);
            await dispatch("validateFlow", {
                flow: isCreating ? flowYaml : getters.yamlWithNextRevision
            });
        },
        fetchGraph({state, dispatch}) {
            return dispatch("loadGraphFromSource", {
                flow: state.flowYaml,
                config: {
                    params: {
                        // due to usage of axios instance instead of $http which doesn't convert arrays
                        subflows: state.expandedSubflows.join(","),
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
                    commit("setFlow", response.data);
                    commit("setFlowYaml", response.data.source);
                    commit("setFlowYamlOrigin", response.data.source);
                    commit("setFlowYamlBeforeAdd", response.data.source);
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
        saveFlow({commit}, options) {
            const flowData = YAML_UTILS.parse(options.flow)
            return this.$http.put(`${apiUrl(this)}/flows/${flowData.namespace}/${flowData.id}`, options.flow, textYamlHeader)
                .then(response => {
                    if (response.status >= 300) {
                        return Promise.reject(new Error("Server error on flow save"))
                    } else {
                        commit("setFlow", response.data);
                        commit("editor/setTabDirty", {
                            name: "Flow",
                            dirty: false,
                        }, {root: true});

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
        deleteFlowAndDependencies({getters, dispatch}){
            const metadata = getters["flowYamlMetadata"];

            return new Promise((resolve, reject) => this.$http
                .get(
                    `${apiUrl(this)}/flows/${metadata.namespace}/${
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
                            resolve(dispatch("deleteFlow", metadata));
                        })
                }).catch(reject)
            )
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
                return response.data;
            })
        },
        loadGraphFromSource({commit, state}, options) {
            const config = options.config ? {...options.config, ...textYamlHeader} : textYamlHeader;
            const flowParsed = YAML_UTILS.parse(options.flow);
            let flowSource = options.flow
            if (!flowParsed.id || !flowParsed.namespace) {
                flowSource = YAML_UTILS.updateMetadata(flowSource, {id: "default", namespace: "default"})
            }
            return this.$http.post(`${apiUrl(this)}/flows/graph`, flowSource, {...config, withCredentials: true})
                .then(response => {
                    commit("setFlowGraph", response.data)

                    let flow = YAML_UTILS.parse(options.flow);
                    flow.id = state.flow?.id ?? flow.id;
                    flow.namespace = state.flow?.namespace ?? flow.namespace;
                    flow.source = options.flow;
                    // prevent losing revision when loading graph from source
                    flow.revision = state.flow?.revision;
                    commit("setFlow", flow);

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
        getGraphFromSourceResponse(_, options) {
            const config = options.config ? {...options.config, ...textYamlHeader} : textYamlHeader;
            const flowParsed = YAML_UTILS.parse(options.flow);
            let flowSource = options.flow
            if (!flowParsed.id || !flowParsed.namespace) {
                flowSource = YAML_UTILS.updateMetadata(flowSource, {id: "default", namespace: "default"})
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
            return this.$http.post(`${apiUrl(this)}/flows/import`, Utils.toFormData(options), {
                headers: {"Content-Type": "multipart/form-data"}
            }).then(response => {
                return response;
            });
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
            return this.$http.post(`${apiUrl(this)}/flows/validate`, options.flow, {...textYamlHeader, withCredentials: true})
                .then(response => {
                    commit("setFlowValidation", response.data[0])
                    return response.data[0]
                })
        },
        validateTask({commit}, options) {
            return this.$http.post(`${apiUrl(this)}/flows/validate/task`, options.task, {...textYamlHeader, withCredentials: true, params: {section: options.section}})
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
        setIsCreating(state, value) {
            state.isCreating = value
        },
        setFlowYamlOrigin(state, value) {
            state.flowYamlOrigin = value
        },
        setFlowYamlBeforeAdd(state, value) {
            state.flowYamlBeforeAdd = value
        },
        setHaveChange(state, value) {
            state.haveChange = value
        },
        setExpandedSubflows(state, value) {
            state.expandedSubflows = value
        },
        setMetadata(state, value) {
            state.metadata = value
        },
    },
    getters: {
        isFlow(state, _getters, rootState) {
            const currentTab = rootState.editor.current;
            return currentTab?.flow !== undefined || state.isCreating;
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
        flowYaml(state) {
            return state.flowYaml;
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
        isReadOnly(_state, getters) {
            return getters.flow?.deleted || !getters.isAllowedEdit || getters.readOnlySystemLabel;
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
        flowErrors(state, getters){
            if (getters.isFlow) {
                const flowExistsError =
                    state.flowValidation?.outdated && state.isCreating
                        ? [`>>>>${getters.baseOutdatedTranslationKey}`] // because translating is impossible here
                        : [];

                const constraintsError =
                    state.flowValidation?.constraints?.split(/, ?/) ?? [];

                const errors = [...flowExistsError, ...constraintsError];

                return errors.length === 0 ? undefined : errors;
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
                return flow ? YAML_UTILS.flowHaveTasks(flow) : false;
            } else return false;
        },
        nextRevision(_state, getters){
            return getters.flow.revision + 1;
        },
        yamlWithNextRevision(_state, getters){
            return `revision: ${getters.nextRevision}\n${getters.flowYaml}`;
        },
        flowParsed(state){
            try{
                return YAML_UTILS.parse(state.flowYaml)
            }catch{
                return undefined
            }
        },
        namespace(state){
            return state.flow?.namespace;
        },
        id(state){
            return state.flow?.id;
        },
        flowYamlMetadata(state){
            return YAML_UTILS.getMetadata(state.flowYaml);
        }
    }
}
