import {computed, h, ref, watch} from "vue"
import {KsMarkdown, KsMessageBox, routeQueryToQueryFilters} from "@kestra-io/design-system"
import resource from "../models/resource"
import action from "../models/action"
import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
import * as Utils from "../utils/utils"
import {apiUrl} from "override/utils/route"
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
import {useClient, type FlowWithSource, type AbstractTrigger, type Task as SdkTask} from "@kestra-io/kestra-sdk"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import * as MetricsAPI from "@kestra-io/kestra-sdk/metrics"
import {defaultNamespace} from "../composables/useNamespaces"

const textYamlHeader = {
    headers: {
        "Content-Type": "application/x-yaml",
    },
}

// backfill (Schedule) and key (Webhook) are trigger-type-specific runtime config, not part of
// the SDK's generic AbstractTrigger schema (which models fields common to every trigger type).
export type Trigger = AbstractTrigger & {
    backfill?: {
        start?: string;
    };
    key?: string;
}

// tasks nest recursively (Sequential, Parallel, EachSequential, ...), which the SDK's flat,
// generic Task schema doesn't model - re-added here for this app's tree-walking usage.
export type Task = SdkTask & {
    tasks?: Task[]
}

export interface Input {
    id: string;
    type: InputType;
    required?: boolean;
    defaults?: any;
}

export interface FlowValidations {
    constraints?: string;
    outdated?: boolean;
    infos?: string[];
    warnings?: string[];
    deprecationPaths?: string[];
}

// tasks/errors/triggers/inputs are overridden below: the SDK's generic Task/AbstractTrigger/
// InputObject types don't model the recursive subtasks or OSS-specific fields (backfill) this
// app relies on for tree-walking and trigger editing. source is narrowed to required since this
// store only ever loads flows with the `source: true` request param, which always returns it.
// disabled/draft/deleted/tasks are widened back to optional: flowStore.flow is also used to hold
// locally-constructed in-progress flows (new flow/file creation) that don't set them yet.
export type Flow = Omit<FlowWithSource, "disabled" | "draft" | "deleted" | "tasks"> & {
    source: string;
    disabled?: boolean;
    draft?: boolean;
    deleted?: boolean;
    triggers?: Trigger[];
    inputs?: Input[];
    errors?: Task[];
    tasks?: Task[];
}

export type FlowSaveOutcome =
    | "saved"
    | "redirect_to_update"
    | "blocked"
    | "no_op";

export function isSuccessfulFlowSaveOutcome(
    outcome: FlowSaveOutcome | null | undefined,
): outcome is "saved" | "redirect_to_update" {
    return outcome === "saved" || outcome === "redirect_to_update"
}

export const useFlowStore = defineStore("flow", () => {
    const flows = ref<Flow[]>()
    const flow = ref<Flow>()
    const search = ref<any[]>()
    const total = ref<number>(0)
    const flowGraph = ref<FlowGraph>()
    const invalidGraph = ref<boolean>(false)
    const revisions = ref<any[]>()
    const revisionsCount = ref<number>()
    const dependenciesCount = ref<number>()
    const filesSaveAll = ref<(() => Promise<void>) | null>(null)
    const hasDirtyEditorFiles = ref<boolean>(false)
    const flowValidation = ref<FlowValidations>()
    const taskError = ref<string>()
    const metrics = ref<any[]>()
    const tasksWithMetrics = ref<any[]>()
    const executeFlow = ref<boolean>(false)
    const isCreating = ref<boolean>(false)
    const readonlyToastShown = ref(false)
    const flowYaml = ref<string>("")
    const flowYamlOrigin = ref<string>("")
    const previewSource = ref<string | undefined>(undefined)
    const expandedSubflows = ref<string[]>([])
    const creationId = ref<string>()

    const axios = useClient()

    const coreStore = useCoreStore()
    const unsavedChangesStore = useUnsavedChangesStore()

    const t = (key: string, values?: Record<string, any>) => {
        if (!globalI18n.value) {
            return key
        }
        return (values ? globalI18n.value?.t(key, values) : globalI18n.value?.t(key)) ?? key
    }

    const haveChange = computed(() => flowYamlOrigin.value !== flowYaml.value)

    watch(haveChange, (newValue) => {
        unsavedChangesStore.unsavedChange = newValue
    })

    async function saveAll(draft?: boolean): Promise<FlowSaveOutcome> {
        const isDraft = draft ?? flow.value?.draft ?? false

        if (!haveChange.value && !isCreating.value) {
            return "no_op"
        }

        if (flowErrors.value?.length && !isDraft) {
            return "blocked"
        }

        if (!flow.value) return "blocked"
        const source = flowYaml.value
        const validation = await onEdit({source})
        if (validation?.outdated && !isCreating.value && !(await confirmOutdatedSave())) {
            return "no_op"
        }
        const outcome = await saveWithoutRevisionGuard(isDraft)
        if (isSuccessfulFlowSaveOutcome(outcome)) {
            flowYamlOrigin.value = source
        }
        return outcome
    }

    function confirmOutdatedSave(): Promise<boolean> {
        const key = "outdated revision save confirmation.update"
        return KsMessageBox({
            title: t(`${key}.title`),
            message: () => h("div", null, [
                h("p", null, `${t(`${key}.description`)} ${t(`${key}.details`)}`),
            ]),
            showCancelButton: true,
            confirmButtonText: t("ok"),
            cancelButtonText: t("cancel"),
            center: false,
            showClose: false,
        }).then(() => true).catch(() => false)
    }

    async function saveAsDraft(): Promise<FlowSaveOutcome> {
        return saveAll(true)
    }

    const route = useRoute()

    const getNamespace = () => {
        return route.query.namespace || defaultNamespace()
    }

    async function save(draft: boolean = false): Promise<FlowSaveOutcome> {
        if (flowErrors.value?.length && !draft) {
            return "blocked"
        }

        const source = flowYaml.value

        if (source) {
            const validation = await onEdit({source})
            if (validation?.outdated && !isCreating.value && !(await confirmOutdatedSave())) {
                return "no_op"
            }
            const outcome = await saveWithoutRevisionGuard(draft)
            if (isSuccessfulFlowSaveOutcome(outcome)) {
                flowYamlOrigin.value = source
            }

            return outcome
        }

        return "no_op"
    }

    async function publishDraft(target?: Flow): Promise<FlowSaveOutcome> {
        if (target) {
            const data = await loadFlow({namespace: target.namespace, id: target.id, store: false})
            if (!data?.source) return "blocked"
            await saveFlow({flow: data.source, draft: false})
            notifySaved(data.id, false)
            return "saved"
        }
        if (!flowYaml.value && flow.value?.source) {
            flowYaml.value = flow.value.source
        }
        return save(false)
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

                    if (!readonlyToastShown.value) {
                        readonlyToastShown.value = true
                        coreStore.message = {
                            variant: "warning",
                            title: t("readonly property"),
                            message: t("namespace and id readonly"),
                        }
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

    function notifySaved(name: string, draft: boolean) {
        if (draft) {
            toast.success(
                t("saved as draft done", {name}),
                t("saved as draft"),
            )
        } else {
            toast.saved(name)
        }
    }

    async function saveWithoutRevisionGuard(draft: boolean = false): Promise<FlowSaveOutcome> {
        const flowSource = flowYaml.value ?? ""

        if (flowParsed.value === undefined && !draft) {
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
                const response = await createFlow({flow: flowSource ?? "", draft})
                notifySaved(response.id, draft)
                isCreating.value = false
            } catch (error: any) {
                if (error?.response?.status === 422 && error?.response?.data?.message?.includes("Flow id already exists")) {
                    const shouldRedirect = await KsMessageBox({
                        title: t("confirmation"),
                        message: () => h(KsMarkdown, {content: t("flow already exists message", {id: flowParsed.value?.id ?? "", namespace: flowParsed.value?.namespace ?? ""})}),
                        type: "warning",
                        showCancelButton: true,
                    }).then(async () => {
                        const response = await saveFlow({flow: flowSource, draft})
                        notifySaved(response.id, draft)
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
            await saveFlow({flow: flowSource, draft})
                .then((response: Flow) => {
                    notifySaved(response.id, draft)
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
        flowYaml.value = source
        flowYamlOrigin.value = source
        if (flowHaveTasks.value) {
            fetchGraph()
        }

        // validate flow on first load
        return validateFlow({flow: isCreating.value ? source : yamlWithNextRevision.value})
    }

    function toFlowSearchParams(options: {[key: string]: any}) {
        const {sort, onlyTotal: _onlyTotal, commit: _commit, page, size, ...filterKeys} = options
        return {
            page,
            size,
            sort: sort ? [sort] : undefined,
            filters: routeQueryToQueryFilters(filterKeys),
        } as Parameters<typeof FlowsAPI.searchFlows>[0]
    }

    function findFlows(options: { [key: string]: any }): Promise<any> {
        return FlowsAPI.searchFlows(toFlowSearchParams(options)).then(response => {
            if (options.onlyTotal) {
                return response.total
            }

            else {
                if (options.commit !== false) {
                    flows.value = response.results as unknown as Flow[]
                    total.value = response.total ?? 0
                }

                return response
            }
        })
    }
    function searchFlows(options: { [key: string]: any }) {
        const {sort, ...rest} = options
        return FlowsAPI.searchFlowsBySourceCode({...rest, sort: sort ? [sort] : undefined}).then(response => {
            search.value = response.results as unknown as any[]
            total.value = response.total ?? 0

            return response
        })
    }

    function flowsByNamespace(namespace: string) {
        return FlowsAPI.listFlowsByNamespace({namespace}).then(response => {
            return response
        })
    }

    async function loadFlow(options: { namespace: string, id: string, revision?: string, allowDeleted?: boolean, source?: boolean, store?: boolean, deleted?: boolean }) {
        let data: Flow & {exception?: string}
        try {
            data = await FlowsAPI.flow({
                namespace: options.namespace,
                id: options.id,
                revision: options.revision ? Number(options.revision) : undefined,
                allowDeleted: options.allowDeleted,
                source: true,
            }) as Flow & {exception?: string}
        } catch (e: any) {
            if (options.deleted && e.status === 404) {
                return e.body ?? {}
            }
            throw e
        }

        if (options.store === false) {
            return data
        }

        if (data.exception) {
            coreStore.message = {
                title: "Invalid source code",
                message: data.exception,
                variant: "error",
            }

            // add this error to the list of errors
            flowValidation.value = {
                constraints: data.exception,
                outdated: false,
                infos: [],
            }
            delete data.exception
        }

        validateFlow({
            flow: `revision: ${(data.revision ?? 0) + 1}\n${data.source}`,
        })

        flow.value = data
        flowYaml.value = data.source
        flowYamlOrigin.value = data.source
        previewSource.value = undefined
        readonlyToastShown.value = false

        return data
    }
    function loadTask(options: { namespace: string, id: string, taskId: string, revision?: string }) {
        return FlowsAPI.taskFromFlow({
            namespace: options.namespace,
            id: options.id,
            taskId: options.taskId,
            revision: options.revision ? Number(options.revision) : undefined,
        })
            .then(data => {
                return data
            })
            .catch((e: any) => {
                if (e.status === 404) return null
                throw e
            })
    }
    function saveFlow(options: { flow: string, draft?: boolean }) {
        let namespace: string
        let id: string
        try {
            const flowData = YAML_UTILS.parse(options.flow)
            namespace = flowData.namespace
            id = flowData.id
        } catch {
            namespace = flow.value?.namespace ?? ""
            id = flow.value?.id ?? ""
        }
        return FlowsAPI.updateFlow({
            namespace,
            id,
            body: options.flow,
            draft: options.draft ?? false,
        }).then(data => {
            flow.value = data as Flow

            return flow.value
        })
    }
    function updateFlowTask(options: { flow: Flow, task: Task }) {
        return axios
            .patch(`${apiUrl()}/flows/${options.flow.namespace}/${options.flow.id}/${options.task.id}`, options.task).then(response => {
                flow.value = response.data

                return response.data
            })
            .then(f => {
                loadGraph({flow: f})

                return f
            })
    }

    function createFlow(options: { flow: string, draft?: boolean }) {
        return FlowsAPI.createFlow({
            body: options.flow,
            draft: options.draft ?? false,
            showMessageOnError: false,
        } as Parameters<typeof FlowsAPI.createFlow>[0]).then(data => {
            const creationPanels = localStorage.getItem(`el-fl-creation-${creationId.value}`) ?? YAML_UTILS.stringify([])
            localStorage.setItem(`el-fl-${flow.value!.namespace}-${flow.value!.id}`, creationPanels)

            flow.value = data as Flow

            // clean-up
            localStorage.removeItem(`el-fl-creation-${creationId.value}`)
            creationId.value = undefined

            return flow.value
        })
    }

    function loadDependencies(options: { namespace: string, id: string, subtype: "FLOW" | "EXECUTION" }, onlyCount = false) {
        return FlowsAPI.flowDependencies({namespace: options.namespace, id: options.id, expandAll: !onlyCount}).then(data => {
            const totalNodes = data.nodes ? new Set(data.nodes.map((r:{uid:string}) => r.uid)).size : 0
            const count = Math.max(0, totalNodes - 1)
            dependenciesCount.value = count
            return {
                ...(!onlyCount ? {data: transformResponse(data as any, options.subtype)} : {}),
                count,
            }
        })
    }

function deleteFlowAndDependencies() {
    const metadataForDelete = flowYamlMetadata.value

    return FlowsAPI.flowDependencies({namespace: metadataForDelete.namespace, id: metadataForDelete.id, destinationOnly: true})
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
                        "<div style=\"margin-top: var(--ks-spacing-3); padding: var(--ks-spacing-2) var(--ks-spacing-4); border-radius: var(--ks-radius-base); background: var(--ks-bg-warning); border: 1px solid var(--ks-border-warning); color: var(--ks-text-warning);\" role=\"alert\">\n" +
                        "<p style=\"margin: 0;\">\n" +
                        t("dependencies delete flow") +
                        "<ul>\n" +
                        deps +
                        "</ul>\n" +
                        "</p>\n" +
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
        return FlowsAPI.deleteFlow(options).then(() => {
            flow.value = undefined
        })
    }

    function loadGraph(options: { flow: Flow, params?: any }): Promise<any> {
        const flowVar = options.flow
        return FlowsAPI.generateFlowGraph({
            namespace: flowVar.namespace,
            id: flowVar.id,
            revision: flowVar.revision,
            subflows: options.params?.subflows,
        }).then(data => {
            invalidGraph.value = false
            flowGraph.value = data as unknown as FlowGraph
            return data
        }).catch(() => {
            invalidGraph.value = true
        })
    }
    function loadGraphFromSource(options: { flow: string, config?: any }) {
        const subflows: string[] | undefined = options.config?.params?.subflows
            ? String(options.config.params.subflows).split(",").filter(Boolean)
            : undefined
        const flowParsed = YAML_UTILS.parse(options.flow)
        let flowSource = options.flow
        if (!flowParsed.id || !flowParsed.namespace) {
            flowSource = YAML_UTILS.updateMetadata(flowSource, {id: "default", namespace: "default"})
        }
        return FlowsAPI.generateFlowGraphFromSource({subflows, body: flowSource})
            .then(data => {
                flowGraph.value = data as unknown as FlowGraph

                const flowVar = YAML_UTILS.parse(options.flow)
                flowVar.id = flow.value?.id ?? flowVar.id
                flowVar.namespace = flow.value?.namespace ?? flowVar.namespace
                flowVar.source = options.flow
                // prevent losing revision when loading graph from source
                flowVar.revision = flow.value?.revision
                flowVar.draft = flow.value?.draft
                flow.value = flowVar

                return data
            }).catch(error => {
                if (error.status === 422 && (!subflows || subflows.length === 0)) {
                    return Promise.resolve(error.response)
                }

                if ([404, 422].includes(error.status) && subflows && subflows.length > 0) {
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
        const subflows: string[] | undefined = options.config?.params?.subflows
            ? String(options.config.params.subflows).split(",").filter(Boolean)
            : undefined
        const flowParsed = YAML_UTILS.parse(options.flow)
        let flowSource = options.flow
        if (!flowParsed.id || !flowParsed.namespace) {
            flowSource = YAML_UTILS.updateMetadata(flowSource, {id: "default", namespace: "default"})
        }
        return FlowsAPI.generateFlowGraphFromSource({subflows, body: flowSource})
    }

    function loadRevisions(options: { namespace: string, id: string, store?: boolean, allowDeleted?: boolean }): Promise<any[]> {
        return FlowsAPI.listFlowRevisions({namespace: options.namespace, id: options.id}).then(data => {
            if (options.store !== false) {
                revisions.value = data
            }
            revisionsCount.value = Array.isArray(data) ? data.length : 0
            return data
        })
    }

    function loadFlowStats(options: { namespace: string, id: string }) {
        return Promise.allSettled([
            loadRevisions({namespace: options.namespace, id: options.id, store: false}),
            loadDependencies({namespace: options.namespace, id: options.id, subtype: "FLOW"}, true),
        ])
    }

    function clearFlowStats() {
        revisionsCount.value = undefined
        dependenciesCount.value = undefined
    }

    function exportFlowByIds(options: { ids: string[] }) {
        return axios.post(`${apiUrl()}/flows/export/by-ids`, options.ids, {responseType: "blob"})
            .then(response => {
                const blob = new Blob([response.data], {type: "application/octet-stream"})
                const url = window.URL.createObjectURL(blob)
                Utils.downloadUrl(url, "flows.zip")
            })
    }

    function exportFlowByQuery(options: { namespace: string, id: string }) {
        return axios.get(`${apiUrl()}/flows/export/by-query`, {params: options, headers: {"Accept": "application/octet-stream"}})
            .then(response => {
                Utils.downloadUrl(response.request?.responseURL ?? "", "flows.zip")
            })
    }

    async function exportFlowAsCSV(params: any) {
        const response = await axios.get(
            `${apiUrl()}/flows/export/by-query/csv`,
            {params, responseType: "text", headers: {Accept: "text/csv"}},
        )
        const url = window.URL.createObjectURL(new Blob([response.data]))
        const link = document.createElement("a")
        link.href = url
        link.setAttribute("download", "flows.csv")
        document.body.appendChild(link)
        link.click()
        link.remove()
        window.URL.revokeObjectURL(url)
    }

    function importFlows(options: { file: FormData,  failOnError: boolean }) {
         const {file, failOnError} = options
        // Don't set Content-Type - the browser must generate the multipart boundary itself.
        return axios.post(`${apiUrl()}/flows/import`, file, {
            params: {failOnError},
        }).then(response => {
            return response
        })
    }
    function disableFlowByIds(options: { ids: {id: string, namespace: string}[] }) {
        return FlowsAPI.disableFlowsByIds({body: options.ids})
    }
    function disableFlowByQuery(options: Record<string, any>) {
        return FlowsAPI.disableFlowsByQuery({filters: routeQueryToQueryFilters(options)} as Parameters<typeof FlowsAPI.disableFlowsByQuery>[0])
    }
    function enableFlowByIds(options: { ids: {id: string, namespace: string}[] }) {
        return FlowsAPI.enableFlowsByIds({body: options.ids})
    }
    function enableFlowByQuery(options: Record<string, any>) {
        return FlowsAPI.enableFlowsByQuery({filters: routeQueryToQueryFilters(options)} as Parameters<typeof FlowsAPI.enableFlowsByQuery>[0])
    }

    function deleteFlowByIds(options: { ids: {id: string, namespace: string}[] }) {
        return FlowsAPI.deleteFlowsByIds({body: options.ids})
    }

    function deleteFlowByQuery(options: Record<string, any>) {
        return FlowsAPI.deleteFlowsByQuery({filters: routeQueryToQueryFilters(options)} as Parameters<typeof FlowsAPI.deleteFlowsByQuery>[0])
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

        return FlowsAPI.validateFlows({body: options.flow}, {withCredentials: true})
            .then(results => {
                const validResults: any = results[0] ?? {}

                const constraintsArray = [validResults.constraints, flowValidationIssues.constraints].filter(Boolean)

                if (constraintsArray.length) {
                    validResults.constraints = constraintsArray.join("\n")
                } else {
                    delete validResults.constraints
                }

                flowValidation.value = validResults
                return validResults
            })
    }

    function validateTask(options: { task: string, section: string }) {
        return FlowsAPI.validateTask(
            {section: options.section as Parameters<typeof FlowsAPI.validateTask>[0]["section"], body: options.task as any},
            {withCredentials: true, headers: textYamlHeader.headers},
        ).then(result => {
            taskError.value = (result as any).constraints
            return result
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
                return data
            })
    }
    function loadTaskAggregatedMetrics(options: { namespace: string, id: string, taskId: string, metric: string, aggregation?: string, startDate?: string, endDate?: string }) {
        return MetricsAPI.aggregateMetricsFromTask({namespace: options.namespace, flowId: options.id, taskId: options.taskId, metric: options.metric, aggregation: options.aggregation, startDate: options.startDate, endDate: options.endDate})
            .then(data => {
                return data
            })
    }

    function setTrigger({index, trigger}: { index: number, trigger: Trigger }) {
        const flowVar = flow.value ?? {} as Flow

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

    function addTrigger(trigger: Trigger) {
        const flowVar = flow.value ?? {} as Flow

        if (trigger.backfill === undefined) {
            trigger.backfill = {
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
        return FlowsAPI.deleteRevisions({namespace: options.namespace, id: options.id, revisions: [Number(options.revision)]})
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
        if (!flow.value?.labels) {
            return false
        }

        const labelsArray = Array.isArray(flow.value.labels) ? flow.value.labels : Object.entries(flow.value.labels).map(([key, value]) => ({key, value}))

        return labelsArray.some(label => label.key === "system.readOnly" && label.value === "true")
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
            flowValidation.value?.constraints ? [flowValidation.value.constraints] : []

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
        search,
        total,
        flowGraph,
        invalidGraph,
        revisions,
        revisionsCount,
        dependenciesCount,
        filesSaveAll,
        hasDirtyEditorFiles,
        flowValidation,
        taskError,
        metrics,
        tasksWithMetrics,
        executeFlow,
        isCreating,
        flowYaml,
        flowYamlOrigin,
        previewSource,
        haveChange,
        expandedSubflows,
        addTrigger,
        setTrigger,
        removeTrigger,
        setExecuteFlow,
        saveAll,
        saveAsDraft,
        save,
        publishDraft,
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
        loadFlowStats,
        clearFlowStats,
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
        deleteRevision,
    }
})
