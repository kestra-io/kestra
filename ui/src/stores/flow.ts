import {computed, h, ref, watch} from "vue"
import {KsMarkdown, KsMessageBox} from "@kestra-io/design-system"
import {AbstractTrigger, Flow, FlowWithSource, PagedResultsFlow, Task} from "@kestra-io/kestra-sdk"
import {flowYamlUtils as YAML_UTILS} from "@kestra-io/design-system"
import * as FlowAPI from "@kestra-io/kestra-sdk/flows"
import * as MetricsAPI from "@kestra-io/kestra-sdk/metrics"
import resource from "../models/resource"
import action from "../models/action"
import {useCoreStore} from "./core"
import {useUnsavedChangesStore} from "./unsavedChanges"
import {defineStore} from "pinia"
import {FlowGraph} from "@kestra-io/topology/vue-flow-utils"
import {makeToast} from "../utils/toast"
import {InputType} from "../utils/inputs"
import {globalI18n} from "../translations/i18n"
import {transformResponse} from "../components/dependencies/composables/useDependencies"
import {useAuthStore} from "override/stores/auth"
import {useRoute} from "vue-router"
import {defaultNamespace} from "../composables/useNamespaces"
import {TUTORIAL_NAMESPACE} from "../utils/constants"


const textYamlHeader = {
    headers: {
        "Content-Type": "application/x-yaml",
    },
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

export type FlowSaveOutcome =
    | "saved"
    | "redirect_to_update"
    | "confirmOutdatedSaveDialog"
    | "blocked"
    | "no_op";

export function isSuccessfulFlowSaveOutcome(
    outcome: FlowSaveOutcome | null | undefined,
): outcome is "saved" | "redirect_to_update" {
    return outcome === "saved" || outcome === "redirect_to_update"
}

export type {Flow, FlowWithSource}

export const useFlowStore = defineStore("flow", () => {
    const flows = ref<Flow[]>()
    const flow = ref<FlowWithSource>()
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
    const creationId = ref<string>()

    const coreStore = useCoreStore()
    const unsavedChangesStore = useUnsavedChangesStore()

    const t = (key: string, values?: Record<string, any>) => {
        if (!globalI18n.value) {
            return key
        }
        return (values ? globalI18n.value?.t(key, values) : globalI18n.value?.t(key)) ?? key
    }

    function onSaveMetadata() {
        flowYaml.value = YAML_UTILS.updateMetadata(flowYaml.value ?? "", metadata.value ?? {})
        metadata.value = undefined
    }

    const haveChange = computed(() => flowYamlOrigin.value !== flowYaml.value)

    watch(haveChange, (newValue) => {
        unsavedChangesStore.unsavedChange = newValue
    })

    async function saveAll(): Promise<FlowSaveOutcome> {
        if ((!haveChange.value && !isCreating.value) || flowErrors.value?.length) {
            return (!haveChange.value && !isCreating.value) ? "no_op" : "blocked"
        }

        if (!flow.value) return "blocked"
        const source = flowYaml.value
        const outcome = await saveWithoutRevisionGuard()
        if (isSuccessfulFlowSaveOutcome(outcome)) {
            flowYamlOrigin.value = source
        }
        return outcome
    }

    const route = useRoute()

    const getNamespace = () => {
        return route.query.namespace || defaultNamespace()
    }

    async function save(): Promise<FlowSaveOutcome> {
        if (flowErrors.value?.length) {
            return "blocked"
        }

        const source = flowYaml.value

        if (source) {
            const validation = await onEdit({source})
            if (validation?.outdated && !isCreating.value) {
                return "confirmOutdatedSaveDialog"
            }
            const outcome = await saveWithoutRevisionGuard()
            if (isSuccessfulFlowSaveOutcome(outcome)) {
                flowYamlOrigin.value = source
            }

            return outcome
        }

        return "no_op"
    }

    async function onEdit({source, topologyVisible}: {
        source: string,
        editorViewType?: string,
        topologyVisible?: boolean
    }): Promise<FlowValidations | undefined> {
        const flowBeforeEdit = flow.value
        const flowOnValidation = flowParsed.value

        if (!source.trim()?.length) {
            flowValidation.value = {
                constraints: t("flow must not be empty"),
            }
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
                    }
                    flowYaml.value = YAML_UTILS.replaceIdAndNamespace(
                        source,
                        flowBeforeEdit.id,
                        flowBeforeEdit.namespace,
                    )
                }
            } catch{
                // yaml is not always valid
            }
        }

        return validateFlow({
            flow: (isCreating.value ? flowYaml.value : yamlWithNextRevision.value) ?? "",
        })
            .then((value: FlowValidations) => {
                if (
                    topologyVisible &&
                    flowHaveTasks.value &&
                    // avoid sending empty errors
                    // they make the backend fail
                    flowBeforeEdit && (!flowBeforeEdit.errors || flowBeforeEdit.errors.every(e => typeof e.id === "string"))
                ) {
                    if (!value.constraints) fetchGraph()
                }

                return value
            })
    }

    const toast = makeToast(t)

    async function saveWithoutRevisionGuard(): Promise<FlowSaveOutcome> {
        const flowSource = flowYaml.value ?? ""

        if (flowParsed.value === undefined) {
            coreStore.message = {
                variant: "error",
                title: t("invalid flow"),
                message: t("invalid yaml"),
            }

            return "blocked"
        }

        let overrideFlow = false
        if (flowErrors.value) {
            if (flowValidation.value?.outdated && isCreating.value) {
                overrideFlow = await KsMessageBox({
                    title: t("override.title"),
                    message: () => {
                        return h("div", null, [
                            h("p", null, t("override.details")),
                        ])
                    },
                    showCancelButton: true,
                    confirmButtonText: t("ok"),
                    cancelButtonText: t("cancel"),
                    center: false,
                    showClose: false,
                })
                    .then(() => {
                        overrideFlow = true
                        return true
                    })
                    .catch(() => {
                        return false
                    })
            }
        }

        const isCreatingBackup = isCreating.value
        if (isCreating.value && !overrideFlow) {
            try {
                const response = await createFlow({flow: flowSource ?? ""})
                toast.saved(response.id)
                isCreating.value = false
            } catch (error: any) {
                if (error?.response?.status === 422 && error?.response?.data?.message?.includes("Flow id already exists")) {
                    const shouldRedirect = await KsMessageBox({
                        title: t("confirmation"),
                        message: () => h(KsMarkdown, {content: t("flow already exists message", {id: flowParsed.value.id, namespace: flowParsed.value.namespace})}),
                        type: "warning",
                        showCancelButton: true,
                    }).then(async () => {
                        const response = await saveFlow({flow: flowSource})
                        toast.saved(response.id)
                        isCreating.value = false
                        return true
                    })

                    return shouldRedirect ? "redirect_to_update" : "blocked"
                }

                if (error.response?.data) {
                    coreStore.message = {
                        variant: "error",
                        response: error.response,
                        content: error.response.data,
                    }
                }

                throw error
            }
        } else {
            await saveFlow({flow: flowSource})
                .then((response: Flow) => {
                    toast.saved(response.id)
                })
        }

        if (isCreatingBackup || overrideFlow) {
            return "redirect_to_update"
        }

        await validateFlow({
            flow: (isCreatingBackup ? flowSource : yamlWithNextRevision.value) ?? "",
        })

        return "saved"
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
                    return status === 200
                },
            },
        })
    }

    async function initYamlSource() {
        if (!flow.value) return
        const {source} = flow.value
        flowYaml.value = source ?? ""
        flowYamlOrigin.value = source ?? ""
        if (flowHaveTasks.value) {
            fetchGraph()
        }

        // validate flow on first load
        return validateFlow({flow: isCreating.value ? source ?? "" : yamlWithNextRevision.value})
    }

    function findFlows(options: Parameters<typeof FlowAPI.searchFlows>[0] & { onlyTotal: true }): Promise<number>
    function findFlows(options: Parameters<typeof FlowAPI.searchFlows>[0] & { onlyTotal?: false }): Promise<PagedResultsFlow>
    function findFlows(options: Parameters<typeof FlowAPI.searchFlows>[0] & { onlyTotal?: boolean }){
        return FlowAPI.searchFlows(
            options,
        ).then(data => {
            if(options.onlyTotal) {
                return data.total
            }
            flows.value = data.results
            total.value = data.total
            overallTotal.value = data.results.filter((f: any) => f.namespace !== TUTORIAL_NAMESPACE).length

            return data
        })
    }

    function searchFlows(options: { [key: string]: any }) {
        const {sort, ...rest} = options
        return FlowAPI.searchFlowsBySourceCode({
            ...rest,
            ...(sort ? {sort: Array.isArray(sort) ? sort : [sort]} : {}),
        }).then(data => {
            search.value = data.results
            total.value = data.total

            return data
        })
    }

    function flowsByNamespace(namespace: string) {
        return FlowAPI.listFlowsByNamespace({namespace})
    }

    async function loadFlow(options: Parameters<typeof FlowAPI.flow>[0] & { store?: boolean, deleted?: boolean }) {
        const data = await FlowAPI.flow(
                options,
                {
                validateStatus: (status: number) => {
                    return options.deleted ? status === 200 || status === 404 : status === 200
                },
            })

        validateFlow({
            flow: `revision: ${(data.revision ?? 0) + 1}\n${data.source}`,
        })

        if (options.store === false) {
            return data
        }

        flow.value = data
        flowYaml.value = data.source ?? ""
        flowYamlOrigin.value = data.source ?? ""
        overallTotal.value = 1

        return data

    }

    function loadTask(options: { namespace: string, id: string, taskId: string, revision?: string }) {
        return FlowAPI.taskFromFlow(
            {
                namespace: options.namespace,
                id: options.id,
                taskId: options.taskId,
                ...(options.revision ? {revision: Number(options.revision)} : {}),
            },
        )
            .then(data => {
                task.value = data

                return data
            })
            .catch(() => null)
    }
    function saveFlow(options: { flow: string }) {
        const flowData = YAML_UTILS.parse(options.flow)
        return FlowAPI.updateFlow({namespace: flowData.namespace, id: flowData.id, body: options.flow})
            .then(data => {
                flow.value = data

                return data
            })
    }

    function createFlow(options: { flow: string }) {
        return FlowAPI.createFlow({body: options.flow})
            .then(data => {
                const creationPanels = localStorage.getItem(`el-fl-creation-${creationId.value}`) ?? YAML_UTILS.stringify([])
                localStorage.setItem(`el-fl-${flow.value!.namespace}-${flow.value!.id}`, creationPanels)

                flow.value = data

                // clean-up
                localStorage.removeItem(`el-fl-creation-${creationId.value}`)
                creationId.value = undefined

                return data
            })
    }

    function loadDependencies(options: Parameters<typeof FlowAPI.flowDependencies>[0] & { subtype: "FLOW" | "EXECUTION" }) {
        return FlowAPI.flowDependencies(options).then(data => {
            return {
                ...(options.expandAll ? {data: transformResponse(data as any, options.subtype)} : {}),
                count: data.nodes ? new Set(data.nodes.map((r:{uid:string}) => r.uid)).size : 0,
            }
        })
    }

function deleteFlowAndDependencies() {
    const metadataForDelete = flowYamlMetadata.value

    return FlowAPI.flowDependencies({
            namespace: metadataForDelete.namespace,
            id: metadataForDelete.id,
            destinationOnly: true,
        })
        .then((data) => {
            let warning = ""
            if (data && data.nodes) {
                const deps = data.nodes
                    .filter(
                        (n: any) =>
                            !(
                                n.namespace === metadataForDelete.namespace &&
                                n.id === metadataForDelete.id
                            ),
                    )
                    .map(
                        (n: any) =>
                            "<li>" +
                            n.namespace +
                            ".<code>" +
                            n.id +
                            "</code></li>",
                    )
                    .join("\n")

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
                        "</div>"
                }
            }
            return t("delete confirm", {name: metadataForDelete.id}) + warning
        })
        .then((message) => {
            return new Promise((resolve, reject) => {
                toast.confirm(message, () => {
                    return deleteFlow({namespace: metadataForDelete.namespace, id: metadataForDelete.id}).then(resolve).catch(reject)
                }, "warning")
            })
        })
        .catch(error => {
            return Promise.reject(error)
        })
}

    function deleteFlow(options: { namespace: string, id: string }) {
        return FlowAPI.deleteFlow({namespace: options.namespace, id: options.id}).then(() => {
            flow.value = undefined
        })
    }

    function loadGraph(options: { flow: Flow, params?: any }) {
        const flowVar = options.flow
        const params = options.params ? options.params : {}
        if (flowVar.revision) {
            params["revision"] = flowVar.revision
        }
        return FlowAPI.generateFlowGraph({namespace: flowVar.namespace, id: flowVar.id, ...params}).then(data => {
            invalidGraph.value = false
            flowGraph.value = data as any
            return data
        }).catch(() => {
            invalidGraph.value = true
        })
    }
    function loadGraphFromSource(options: { flow: string, config?: any }) {
        const config = options.config ? {...options.config, ...textYamlHeader} : textYamlHeader
        const flowParsed = YAML_UTILS.parse(options.flow)
        let flowSource = options.flow
        if (!flowParsed.id || !flowParsed.namespace) {
            flowSource = YAML_UTILS.updateMetadata(flowSource, {id: "default", namespace: "default"})
        }
        return FlowAPI.generateFlowGraphFromSource(
            {
                body: flowSource,
                ...(config?.params?.subflows ? {subflows: config.params.subflows.split(",")} : {}),
            },
        )
            .then(data => {
                flowGraph.value = data as any

                const flowVar = YAML_UTILS.parse(options.flow)
                flowVar.id = flow.value?.id ?? flowVar.id
                flowVar.namespace = flow.value?.namespace ?? flowVar.namespace
                flowVar.source = options.flow
                // prevent losing revision when loading graph from source
                flowVar.revision = flow.value?.revision
                flow.value = flowVar

                return data
            }).catch(error => {
                if (error?.response?.status === 422 && (!config?.params?.subflows || config?.params?.subflows?.length === 0)) {
                    return Promise.resolve(error.response?.data)
                }

                if ([404, 422].includes(error?.response?.status) && config?.params?.subflows?.length > 0) {
                    coreStore.message = {
                        title: "Couldn't expand subflow",
                        message: error.response?.data?.message,
                        variant: "error",
                    }
                }

                return Promise.reject(error)
            })
    }

    function getGraphFromSourceResponse(options: { flow: string, config?: any }) {
        const config = options.config ? {...options.config, ...textYamlHeader} : textYamlHeader
        const flowParsed = YAML_UTILS.parse(options.flow)
        let flowSource = options.flow
        if (!flowParsed.id || !flowParsed.namespace) {
            flowSource = YAML_UTILS.updateMetadata(flowSource, {id: "default", namespace: "default"})
        }
        return FlowAPI.generateFlowGraphFromSource({
            body: flowSource,
            ...(config?.params?.subflows ? {subflows: config.params.subflows.split(",")} : {}),
        })
    }

    function loadRevisions(options: { namespace: string, id: string, store?: boolean, allowDeleted?: boolean }) {
        return FlowAPI.listFlowRevisions({namespace: options.namespace, id: options.id}).then(data => {
            if (options.store !== false) {
                revisions.value = data
            }
            return data
        })
    }

    function validateFlow(options: { flow: string }) {
        const flowValidationIssues: FlowValidations = {}
        if(isCreating.value) {
            const {namespace} = YAML_UTILS.getMetadata(options.flow)
            if(authStore.user && !authStore.user?.isAllowed(
                resource.FLOW,
                action.CREATE,
                namespace,
            )) {
                flowValidationIssues.constraints = t("flow creation denied in namespace", {namespace})
            }
        }

        return FlowAPI.validateFlows({body: options.flow})
            .then(data => {
                const validResults = data[0] ?? {}

                const constraintsArray = [validResults.constraints, flowValidationIssues.constraints].filter(Boolean)

                if (constraintsArray.length) {
                    validResults.constraints = constraintsArray.join(", ")
                } else {
                    delete validResults.constraints
                }

                flowValidation.value = validResults

                return validResults
            })
    }

    function validateTask(options: { task: string, section: string }) {
        return FlowAPI.validateTask({body: options.task as any, section: options.section as any})
            .then(data => {
                taskError.value = data.constraints
                return data
            })
    }
    function loadFlowMetrics(options: { namespace: string, id: string }) {
        return MetricsAPI.listFlowMetrics({namespace: options.namespace, flowId: options.id})
            .then(data => {
                metrics.value = data
                return data
            })
    }
    function loadTaskMetrics(options: { namespace: string, id: string, taskId: string }) {
        return MetricsAPI.listTaskMetrics({namespace: options.namespace, flowId: options.id, taskId: options.taskId})
            .then(data => {
                metrics.value = data
                return data
            })
    }
    function loadTasksWithMetrics(options: { namespace: string, id: string }) {
        return MetricsAPI.listTasksWithMetrics({namespace: options.namespace, flowId: options.id})
            .then(data => {
                tasksWithMetrics.value = data
                return data
            })
    }
    function loadFlowAggregatedMetrics(options: { namespace: string, id: string, metric: string, aggregation?: string, startDate?: string, endDate?: string }) {
        return MetricsAPI.aggregateMetricsFromFlow({namespace: options.namespace, flowId: options.id, metric: options.metric, aggregation: options.aggregation, startDate: options.startDate, endDate: options.endDate})
            .then(data => {
                aggregatedMetrics.value = data
                return data
            })
    }
    function loadTaskAggregatedMetrics(options: { namespace: string, id: string, taskId: string, metric: string, aggregation?: string, startDate?: string, endDate?: string }) {
        return MetricsAPI.aggregateMetricsFromTask({namespace: options.namespace, flowId: options.id, taskId: options.taskId, metric: options.metric, aggregation: options.aggregation, startDate: options.startDate, endDate: options.endDate})
            .then(data => {
                aggregatedMetrics.value = data
                return data
            })
    }

    function setTrigger({index, trigger}: { index: number, trigger: AbstractTrigger }) {
        const flowVar = flow.value ?? {} as FlowWithSource

        if (flowVar.triggers === undefined) {
            flowVar.triggers = []
        }

        flowVar.triggers[index] = trigger

        flow.value = {...flowVar}
    }

    function removeTrigger(index: number) {
        const flowVar = flow.value ?? {} as Flow
        flowVar.triggers?.splice(index, 1)

        flow.value = {...flowVar}
    }

    function setExecuteFlow(value: boolean) {
        executeFlow.value = value
    }

    function setOpenAiCopilot(value: boolean) {
        openAiCopilot.value = value
    }

    function addTrigger(trigger: AbstractTrigger) {
        const flowVar = flow.value ?? {} as Flow

        if ((trigger as any).backfill === undefined) {
            (trigger as any).backfill = {
                start: undefined,
            }
        }

        if (flowVar.triggers === undefined) {
            flowVar.triggers = []
        }

        flowVar.triggers.push(trigger)

        flow.value = {...flowVar}
    }

    function deleteRevision(options: { namespace: string, id: string, revision: string }) {
        return FlowAPI.deleteRevisions({namespace: options.namespace, id: options.id, revisions: [Number(options.revision)]})
    }

    const authStore = useAuthStore()

    const isAllowedEdit = computed((): boolean => {
        if (!flow.value || !authStore.user) {
            return false
        }

        return (isCreating.value && authStore.user?.hasAnyAction(resource.FLOW, action.UPDATE))
         || authStore.user?.isAllowed(
            resource.FLOW,
            action.UPDATE,
            flow.value?.namespace,
        )
    })

    const readOnlySystemLabel = computed(() => {
        if (!flow.value || !flow.value.labels) {
            return false
        }

        return ((flow.value.labels as any)?.["system.readOnly"] === "true") || ((flow.value.labels as any)?.["system.readOnly"] === true)
    })

    const isReadOnly = computed(() => {
        return flow.value?.deleted || !isAllowedEdit.value || readOnlySystemLabel.value
    })

    const baseOutdatedTranslationKey = computed(() => {
        const createOrUpdateKey = isCreating.value ? "create" : "update"
        return "outdated revision save confirmation." + createOrUpdateKey
    })

    const flowErrors = computed((): string[] | undefined => {
        const key = baseOutdatedTranslationKey.value
        const flowExistsError =
            flowValidation.value?.outdated && isCreating.value
                ? [`${t(key + ".description")} ${t(key + ".details")}`]
                : []

        const constraintsError =
            flowValidation.value?.constraints?.split(/, ?/) ?? []

        const errors = [...flowExistsError, ...constraintsError]

        return errors.length === 0 ? undefined : errors
    })

    const flowInfos = computed(() => {
        const infos = flowValidation.value?.infos ?? []

        return infos.length === 0 ? undefined : infos
    })

    const flowHaveTasks = computed((): boolean => {
        const flowVar = isCreating.value ? flow.value?.source : flowYaml.value
        return flowVar ? YAML_UTILS.flowHaveTasks(flowVar) : false
    })

    const nextRevision = computed((): number => {
        return (flow.value?.revision ?? 0) + 1
    })

    const yamlWithNextRevision = computed((): string => {
        if (!flowYaml.value) return ""
        return `revision: ${nextRevision.value}\n${flowYaml.value}`
    })

    const flowParsed = computed(() => {
        try {
            return YAML_UTILS.parse(flowYaml.value)
        } catch {
            return undefined
        }
    })
    const flowYamlMetadata = computed(() => {
        return YAML_UTILS.getMetadata(flowYaml.value ?? "")
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
        createFlow,
        loadDependencies,
        deleteFlowAndDependencies,
        deleteFlow,
        loadGraph,
        loadGraphFromSource,
        getGraphFromSourceResponse,
        loadRevisions,
        validateFlow,
        validateTask,
        loadFlowMetrics,
        loadTaskMetrics,
        loadFlowAggregatedMetrics,
        loadTaskAggregatedMetrics,
        loadTasksWithMetrics,
        getNamespace,
        deleteRevision,
    }
})
