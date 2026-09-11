import {defineStore} from "pinia"
import {ref, watch} from "vue"
import {apiUrl} from "override/utils/route"
import * as Utils from "../utils/utils"
import {useCoreStore} from "./core"
import throttle from "lodash/throttle"
import {useRoute, type LocationQuery} from "vue-router"
import {CLUSTER_PREFIX} from "@kestra-io/design-system"
import type {FlowGraph} from "@kestra-io/topology/vue-flow-utils"
import {routeQueryToQueryFilters} from "../utils/queryFilters"
import {
    TaskRun,
    useClient,
    type Execution as SDKExecution,
    type ExecutionRepositoryInterfaceFlowFilter,
    type FlowForExecution,
    type Label,
    type Level,
    type LogEntry,
    type PagedResultsApiLightExecution,
    type StateType,
} from "@kestra-io/kestra-sdk"
import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
import * as LogsAPI from "@kestra-io/kestra-sdk/logs"
import * as ExecutionUtils from "../utils/executionUtils"
import {executionLogsDownloadFilename} from "../utils/logs"
import {InputType} from "../utils/inputs"
import {Optional} from "../utils/utils"
import {useApiStore} from "./api"
import {executionLocation, isExampleFlow} from "../utils/analytics/activation"
import type {KestraRequestOptions} from "../utils/kestraHttp"

export interface Check {
    message: string
    style: string
    behavior: string
}

export interface InputError {
    message: string;
    // true when the error is a render/resolution failure (broken field: e.g. a SELECT `expression` or an
    // input `defaults` Pebble expression that threw) rather than a value validation error
    renderError?: boolean;
}

export interface ValidationResponse {
    checks?: Check[];
    inputs: Array<{
        enabled: boolean;
        input: InputMetaData;
        errors?: InputError[];
        value?: unknown;
        isDefault?: boolean;
    }>;
}

export interface ValidationEventPayload {
    formData: FormData | undefined;
    inputsMetaData: InputMetaData[];
    callback: (response: ValidationResponse) => void;
}

export type ValueOptionLike = string | {label: string; value: string};

export interface InputMetaData {
    id: string;
    type: InputType
    displayName?: string;
    description?: string;
    required?: boolean;
    defaults?: unknown;
    value?: unknown;
    values?: ValueOptionLike[];
    options?: ValueOptionLike[];
    errors?: InputError[];
    isDefault?: boolean;
    isRadio?: boolean;
    allowCustomValue?: boolean;
    min?: number;
    max?: number;
    allowedFileExtensions?: string[];
    accept?: string;
    prefill?: unknown;
    // present only on the raw flow inputs (props.initialInputs); the rendered
    // validate response strips `expression`, keeping `dependsOn` at most
    expression?: string;
    dependsOn?: unknown;
}

/** Mirrors the backend `FilePreview`: `content` is renderer-specific (text, rows, base64, ...). */
export interface FilePreview {
    extension?: string;
    type?: "TEXT" | "LIST" | "IMAGE" | "MARKDOWN" | "PDF";
    content?: unknown;
    truncated?: boolean;
}

/**
 * A route query only whose `filters[...]` keys reach the backend as filters. Left open because the
 * bulk-action dispatcher merges the action's own options into the same bag (see `Executions.vue`).
 */
type FilterQuery = Record<string, unknown>

/** Route query of the executions list plus the paging and commit options `findExecutions` reads. */
type ExecutionSearchOptions = FilterQuery & {
    page?: number;
    size?: number;
    sort?: string;
    /** `false` returns the page without replacing the store's `executions`/`total`. */
    commit?: boolean;
    onlyTotal?: boolean;
}

type DeleteExecutionOptions = {
    includeNonTerminated?: boolean;
    deleteLogs?: boolean;
    deleteMetrics?: boolean;
    deleteStorage?: boolean;
}

type GraphOptions = {
    id: string;
    /** `subflows` lists the subflow paths whose own graph is expanded inline. */
    params?: { subflows?: string[] };
}

type FlowGraphNode = FlowGraph["nodes"][number]

export function normalizeFilePreview(data: FilePreview): FilePreview {
    const rows = data?.content
    if (data?.extension !== "ion" || !Array.isArray(rows)) {
        return data
    }

    // WORKAROUND, related to https://github.com/kestra-io/plugin-aws/issues/456
    const notObjects = rows.some((row: unknown) => typeof row !== "object")

    if (!notObjects) {
        return data
    }

    const content = rows.length === 1 ? rows[0] : rows.join("\n")
    return {...data, type: "TEXT", content}
}

export type {Label, StateHistory as Histories} from "@kestra-io/kestra-sdk"

export type Execution = Omit<Optional<SDKExecution, "deleted">, "taskRunList"> & {
    tenantId?: string;
    taskRunList?: Optional<TaskRun, "namespace" | "executionId" | "flowId">[];
    inputs?: Record<string, unknown>;
    variables?: Record<string, unknown>;
}

export const useExecutionsStore = defineStore("executions", () => {
    // State
    const executions = ref<Execution[] | undefined>(undefined)
    const execution = ref<Execution | undefined>(undefined)
    const total = ref<number>(0)
    const logs = ref<LogEntry[]>([])
    const subflowsExecutions = ref<Record<string, Execution>>({})
    // live lifecycle-step progress reported by plugins mid-run (see RunContext#emitProgress),
    // read off the follow-logs SSE stream; taskRunId is globally unique so this is safe to
    // never reset across execution navigations, like subflowsExecutions above
    const progressEvents = ref<{taskId: string; taskRunId: string; step: string; timestamp: string}[]>([])
    const flow = ref<FlowForExecution | undefined>(undefined)
    const flowGraph = ref<FlowGraph | undefined>(undefined)
    const namespaces = ref<string[]>([])
    const flowsExecutable = ref<FlowForExecution[]>([])

    // clear flow graph when execution is reset
    // since it is supposed to represent the current execution's flow
    watch(execution, (newExecution) => {
        if(!newExecution){
            flowGraph.value = undefined
            flow.value = undefined
        }
    })

    const coreStore = useCoreStore()
    const axios = useClient()

    // Actions
    const restartExecution = (options: { executionId: string; revision?: number }) => {
        return ExecutionsAPI.restartExecution({executionId: options.executionId, revision: options.revision}) as unknown as Promise<Execution>
    }

    const bulkRestartExecution = (options: { executionsId: string[]; latestRevision?: boolean }) => {
        return ExecutionsAPI.restartExecutionsByIds({body: options.executionsId, latestRevision: options.latestRevision})
    }

    const queryRestartExecution = (options: FilterQuery & { latestRevision?: boolean }) => {
        return ExecutionsAPI.restartExecutionsByQuery({filters: routeQueryToQueryFilters(options), latestRevision: options.latestRevision})
    }

    const bulkResumeExecution = (options: { executionsId: string[] }) => {
        return ExecutionsAPI.resumeExecutionsByIds({body: options.executionsId})
    }

    const queryResumeExecution = (options: FilterQuery) => {
        return ExecutionsAPI.resumeExecutionsByQuery({filters: routeQueryToQueryFilters(options)})
    }

    const bulkReplayExecution = (options: { executionsId: string[]; latestRevision?: boolean }) => {
        return ExecutionsAPI.replayExecutionsByIds({body: options.executionsId, latestRevision: options.latestRevision})
    }

    const bulkChangeExecutionStatus = (options: { executionsId: string[]; newStatus: StateType }) => {
        return ExecutionsAPI.updateExecutionsStatusByIds({body: options.executionsId, newStatus: options.newStatus})
    }

    const queryReplayExecution = (options: FilterQuery & { latestRevision?: boolean }) => {
        return ExecutionsAPI.replayExecutionsByQuery({filters: routeQueryToQueryFilters(options), latestRevision: options.latestRevision})
    }

    const queryChangeExecutionStatus = (options: FilterQuery & { newStatus: StateType }) => {
        return ExecutionsAPI.updateExecutionsStatusByQuery({filters: routeQueryToQueryFilters(options), newStatus: options.newStatus})
    }

    const replayExecution = (options: { executionId: string; taskRunId?: string; revision?: number, breakpoints?: string[] }) => {
        return ExecutionsAPI.replayExecution({
            executionId: options.executionId,
            taskRunId: options.taskRunId,
            revision: options.revision,
            breakpoints: options.breakpoints ? options.breakpoints.join(",") : undefined,
        }) as unknown as Promise<Execution>
    }

    // Stays on raw axios: multipart form-data body (file inputs), not a clean typed JSON call.
    // Don't set Content-Type - the browser must generate the multipart boundary itself; an
    // explicit "multipart/form-data" header (needed under the old axios client) has no boundary
    // and corrupts the request.
    const replayExecutionWithInputs = (options: { executionId: string; taskRunId?: string; revision?: number, breakpoints?: string[], formData?: FormData }) => {
        return axios.post<Execution>(
            `${apiUrl()}/executions/${options.executionId}/actions/replay-with-inputs`,
            options.formData,
            {
                params: {
                    taskRunId: options.taskRunId,
                    revision: options.revision,
                    breakpoints: options.breakpoints ? options.breakpoints.join(",") : undefined,
                },
            }).then(response => response.data)
    }

    const changeExecutionStatus = (options: { executionId: string; state: string }) => {
        return ExecutionsAPI.updateExecutionStatus({executionId: options.executionId, status: options.state as Parameters<typeof ExecutionsAPI.updateExecutionStatus>[0]["status"]}) as unknown as Promise<Execution>
    }

    const changeStatus = (options: { executionId: string; taskRunId?: string; state: string }) => {
        return ExecutionsAPI.updateTaskRunState({
            executionId: options.executionId,
            taskRunId: options.taskRunId!,
            state: options.state as Parameters<typeof ExecutionsAPI.updateTaskRunState>[0]["state"],
        }) as unknown as Promise<Execution>
    }
    const interrupt = (options: { executionId: string; taskRunId: string; state: string }) => {
        return axios.post(`${apiUrl()}/executions/${options.executionId}/actions/interrupt`, {
            taskRunId: options.taskRunId,
            state: options.state,
        }).then(response => response.data) as Promise<Execution>
    }
    const waitForStateChange = async (source: Execution) => {
        const updated = await ExecutionUtils.waitForState(axios, source) as Execution
        execution.value = updated
        return updated
    }

    const kill = (options: { id: string; isOnKillCascade?: boolean }) => {
        return ExecutionsAPI.killExecution({executionId: options.id, isOnKillCascade: options.isOnKillCascade}) as unknown as Promise<Execution>
    }

    const bulkKill = (options: { executionsId: string[] }) => {
        return ExecutionsAPI.killExecutionsByIds({body: options.executionsId})
    }

    const queryKill = (options: FilterQuery) => {
        return ExecutionsAPI.killExecutionsByQuery({filters: routeQueryToQueryFilters(options)})
    }

    // Stays on raw axios: multipart form-data body (file inputs), not a clean typed JSON call.
    // Don't set Content-Type - the browser must generate the multipart boundary itself.
    const resume = (options: { id: string; formData?: FormData }) => {
        return axios.post<Execution>(`${apiUrl()}/executions/${options.id}/actions/resume`, Utils.toFormData(options.formData ?? {}), {
            timeout: 60 * 60 * 1000,
        }).then(response => response.data)
    }

    // Stays on raw axios: multipart form-data body (file inputs), not a clean typed JSON call.
    // Don't set Content-Type - the browser must generate the multipart boundary itself.
    const validateResume = (options: { id: string; formData?: FormData }) => {
        return axios.post<ValidationResponse>(`${apiUrl()}/executions/${options.id}/actions/resume/validate`, Utils.toFormData(options.formData ?? {}), {
            timeout: 60 * 60 * 1000,
        }).then(response => response.data)
    }

    // Stays on raw axios: no matching endpoint exposed by the generated SDK.
    const resumeFromBreakpoint = (options: { id: string; breakpoints?: string[] }) => {
        return axios.post<Execution>(
            `${apiUrl()}/executions/${options.id}/actions/resume-from-breakpoint`,
            null,
            {
                params: {
                    breakpoints: options.breakpoints ? options.breakpoints.join(",") : undefined,
                },
            },
        ).then(response => response.data)
    }

    const pause = (options: { id: string }) => {
        return ExecutionsAPI.pauseExecution({executionId: options.id}) as unknown as Promise<Execution>
    }

    const bulkPauseExecution = (options: { executionsId: string[] }) => {
        return ExecutionsAPI.pauseExecutionsByIds({body: options.executionsId})
    }

    const queryPauseExecution = (options: FilterQuery) => {
        return ExecutionsAPI.pauseExecutionsByQuery({filters: routeQueryToQueryFilters(options)})
    }

    let latestExecutionLoad = 0

    const loadExecution = (options: { id: string }, requestOptions?: KestraRequestOptions) => {
        const load = ++latestExecutionLoad
        return ExecutionsAPI.execution({executionId: options.id}, requestOptions).then(data => {
            // A load the user has navigated away from must neither become the execution on screen
            // nor drop the pending update for the one that is, the same way a superseded search is
            // dropped in `stores/logs.ts`.
            if (load !== latestExecutionLoad) return data

            // A trailing event from the previous execution's stream, still open until the page
            // mounts and follows this one, would otherwise land on top of this load.
            throttledExecutionUpdate.cancel()
            execution.value = data
            return execution.value
        })
    }

    function toExecutionSearchParams(options: ExecutionSearchOptions) {
        return {
            page: options.page,
            size: options.size,
            sort: options.sort ? [options.sort] : undefined,
            filters: routeQueryToQueryFilters(options),
        }
    }

    /** With `onlyTotal`, resolves to the number of matches instead of the page of results. */
    function findExecutions(options: ExecutionSearchOptions & { onlyTotal: true }): Promise<number>
    function findExecutions(options: ExecutionSearchOptions): Promise<PagedResultsApiLightExecution>
    function findExecutions(options: ExecutionSearchOptions): Promise<PagedResultsApiLightExecution | number> {
        return ExecutionsAPI.searchExecutions(toExecutionSearchParams(options)).then(response => {
            if (options.commit !== false) {
                executions.value = response.results as unknown as Execution[]
                total.value = response.total ?? 0
            }

            if (options.onlyTotal) {
                return response.total
            }

            return response
        })
    }

    const findDistinctFieldValues = async (options: {
        field: string;
        filters?: LocationQuery;
        size?: number;
    }): Promise<string[]> => {
        return ExecutionsAPI.findDistinctFieldValues({
            field: options.field as Parameters<typeof ExecutionsAPI.findDistinctFieldValues>[0]["field"],
            filters: options.filters ? routeQueryToQueryFilters(options.filters) : undefined,
            size: options.size ?? 100,
        })
    }

    // Stays on raw axios: multipart form-data body (file inputs), not a clean typed JSON call.
    // Don't set Content-Type - the browser must generate the multipart boundary itself.
    const validateExecution = (options: { namespace: string; id: string; formData?: FormData; labels?: string[]; scheduleDate?: string }) => {
        return axios.post<ValidationResponse>(`${apiUrl()}/executions/${options.namespace}/${options.id}/validate`, Utils.toFormData(options.formData ?? {}), {
            timeout: 60 * 60 * 1000,
            params: {
                labels: options.labels ?? [],
                scheduleDate: options.scheduleDate,
            },
        }).then(response => response.data)
    }

    const triggerExecution = (options: {
        namespace: string;
        id: string;
        formData?: Record<string, unknown>;
        kind: "PLAYGROUND" | "NORMAL"
        breakpoints?: string[];
        labels?: string[];
        scheduleDate?: string,
        revision?: number,
    }) => {
        // body's generated type is a narrow `Array<Blob | File>` fallback - OpenAPI can't express
        // a dynamic, per-flow-input-keyed object schema - but the runtime multipart serializer just
        // does Object.entries(body), so a plain key/value object of input values works correctly
        // despite the mismatched declared type.
        return ExecutionsAPI.createExecution({
            namespace: options.namespace,
            id: options.id,
            body: options.formData as unknown as Parameters<typeof ExecutionsAPI.createExecution>[0]["body"],
            labels: options.labels ?? [],
            scheduleDate: options.scheduleDate,
            kind: options.kind,
            breakpoints: options.breakpoints ? options.breakpoints.join(",") : undefined,
            revision: options.revision,
        // Don't set Content-Type here - createExecution() already defaults it to null so the
        // browser can generate the multipart boundary itself. An explicit "multipart/form-data"
        // header (needed under the old axios client) has no boundary and corrupts the request.
        }, {timeout: 60 * 60 * 1000}).then(execution => {
            useApiStore().posthogEvents({
                type: "FLOW_EXECUTION",
                action: "executed",
                execution_id: execution.id,
                namespace: execution.namespace,
                flow_id: execution.flowId,
                location: executionLocation(route.name?.toString(), options.kind),
                is_example: isExampleFlow(execution.namespace),
                revision: execution.flowRevision,
            })

            return execution
        })
    }

    const deleteExecution = (options: { id: string; deleteLogs?: boolean; deleteMetrics?: boolean; deleteStorage?: boolean }) => {
        return ExecutionsAPI.deleteExecution({
            executionId: options.id,
            deleteLogs: options.deleteLogs,
            deleteMetrics: options.deleteMetrics,
            deleteStorage: options.deleteStorage,
        }).then(() => {
            execution.value = undefined
        })
    }

    const bulkDeleteExecution = (options: { executionsId: string[] } & DeleteExecutionOptions) => {
        const {executionsId, ...rest} = options
        return ExecutionsAPI.deleteExecutionsByIds({body: executionsId, ...rest})
    }

    const queryDeleteExecution = (options: FilterQuery & DeleteExecutionOptions) => {
        return ExecutionsAPI.deleteExecutionsByQuery({
            filters: routeQueryToQueryFilters(options),
            includeNonTerminated: options.includeNonTerminated,
            deleteLogs: options.deleteLogs,
            deleteMetrics: options.deleteMetrics,
            deleteStorage: options.deleteStorage,
        })
    }

    // Handle to the SDK follow stream backing the currently displayed execution.
    // Closing it aborts the underlying stream (see subscribeToExecution).
    const executionSubscription = ref<{ close: () => void } | undefined>(undefined)

    function closeSSE() {
        executionSubscription.value?.close()
        executionSubscription.value = undefined
    }

    const route = useRoute()

    const throttledExecutionUpdate = throttle((parsedExecution: Execution) => {
        const flowValue = flow.value

        if ((!flowValue ||
            parsedExecution.flowId !== flowValue.id ||
            parsedExecution.namespace !== flowValue.namespace ||
            parsedExecution.flowRevision !== flowValue.revision)
        ) {
            loadFlowForExecutionByExecutionId(
                {
                    id: parsedExecution.id,
                    revision: route.query.revision?.toString(),
                },
            ).then(() => {
                execution.value = parsedExecution
            })
        }

        execution.value = parsedExecution
    }, 500)

    /**
     * Subscribe to an execution's live updates through the SDK follow stream.
     *
     * Replaces the previous manual `EventSource` subscription: the SDK yields already
     * parsed {@link Execution} events on an async stream, so callers only provide
     * callbacks. The initial "start" stub (an execution carrying only an id, no state)
     * is skipped, matching the previous `lastEventId === "start"` guard.
     *
     * `onEnd` fires exactly once when the stream terminates. `onError` fires additionally
     * when the stream stops before the terminating "end" event — i.e. a 404 or a lost
     * connection — mirroring the previous EventSource `onerror` semantics.
     *
     * @returns a handle whose `close()` aborts the stream.
     */
    function subscribeToExecution(
        executionId: string,
        handlers: {
            onExecution: (execution: Execution) => void;
            onError?: () => void;
            onEnd?: () => void;
        },
    ): { close: () => void } {
        const controller = new AbortController()
        let closed = false
        let finished = false
        // The server closes the stream with an "end" event on normal completion; a
        // termination without it means the connection dropped or the execution was not found.
        let receivedEnd = false

        const finish = (errored: boolean) => {
            if (finished || closed) return
            finished = true
            if (errored) handlers.onError?.()
            handlers.onEnd?.()
        }

        const close = () => {
            if (closed) return
            closed = true
            controller.abort()
        }

        ExecutionsAPI.followExecution(
            {executionId},
            {
                signal: controller.signal,
                // Do not auto-reconnect on a dropped connection: each reconnect opened a
                // fresh server-side SSE connection whose Netty direct buffers were not
                // promptly reclaimed, leaking off-heap memory over time (kestra-io/kestra#16982).
                sseMaxRetryAttempts: 1,
                onSseEvent: (event: { id?: string }) => {
                    if (event.id === "end") receivedEnd = true
                },
                onSseError: () => finish(true),
            },
        )
            .then(async ({stream}) => {
                for await (const event of stream) {
                    if (closed) break
                    // The server emits a first "fake" event carrying only an id to force the
                    // connection open; skip it as it has no state to display.
                    if (!(event as Execution).state) continue
                    handlers.onExecution(event as Execution)
                }
                finish(!receivedEnd)
            })
            .catch(() => finish(true))

        return {close}
    }

    const followExecution = (options: { id: string }, translate: (itn: string) => string) => {
        // Keep an execution the route guard already loaded: clearing it would send the page back to
        // its loading state, and cost a second fetch of what the store is already holding.
        if (execution.value?.id !== options.id) {
            execution.value = undefined
        }
        closeSSE()

        executionSubscription.value = subscribeToExecution(options.id, {
            onExecution: (parsedExecution) => throttledExecutionUpdate(parsedExecution),
            // The follow emitter can only fail with a 404, so a still-undefined execution
            // means the flow or execution was not found; otherwise the connection was lost.
            onError: () => {
                coreStore.message = !execution.value
                    ? {
                        variant: "error",
                        title: translate("error"),
                        content: translate("errors.404.flow or execution"),
                    }
                    : {
                        variant: "error",
                        title: translate("something_went_wrong.connection_lost.title"),
                        content: translate("something_went_wrong.connection_lost.message"),
                    }
            },
            onEnd: () => {
                throttledExecutionUpdate.flush()
                closeSSE()
            },
        })
    }

    function followExecutionDependencies(options: { id: string; expandAll?: boolean }) {
        return new EventSource(`${apiUrl()}/executions/${options.id}/follow-dependencies${options.expandAll ? "?expandAll=true" : ""}`, {withCredentials: true})
    }

    const followLogs = (options: { id: string; params?: FilterQuery }) => {
        const search = new URLSearchParams()
        Object.entries(options.params ?? {}).forEach(([key, value]) => {
            if (value === undefined || value === null || value === "") return
            if (Array.isArray(value)) {
                value.forEach(item => search.append(key, String(item)))
            } else {
                search.append(key, String(value))
            }
        })
        const query = search.toString()
        return Promise.resolve(new EventSource(`${apiUrl()}/logs/${options.id}/follow${query ? `?${query}` : ""}`, {withCredentials: true}))
    }

    const loadLogs = (options: { executionId: string; params?: FilterQuery; store?: boolean; showMessageOnError?: boolean }) => {
        const requestOptions: KestraRequestOptions | undefined = options.showMessageOnError === false ? {showMessageOnError: false} : undefined
        return LogsAPI.listLogsFromExecution(
            {executionId: options.executionId, filters: routeQueryToQueryFilters(options.params ?? {})},
            requestOptions,
        ).then(data => {
            if (options.store === false) {
                return data
            }
            logs.value = data
            return data
        })
    }

    const downloadLogs = (options: { executionId: string; params?: FilterQuery }) => {
        return LogsAPI.downloadLogsFromExecution({executionId: options.executionId, filters: routeQueryToQueryFilters(options.params ?? {})}) as unknown as Promise<string>
    }

    const downloadLogsFile = (options: { executionId: string; params?: FilterQuery }) => {
        return downloadLogs(options).then(text => {
            Utils.downloadUrl(
                window.URL.createObjectURL(new Blob([text])),
                executionLogsDownloadFilename(options.executionId, new Date()),
            )
        })
    }

    const deleteLogs = (options: { executionId: string; params?: { minLevel?: Level; taskRunId?: string; taskId?: string; attempt?: number } }) => {
        return LogsAPI.deleteLogsFromExecution({executionId: options.executionId, ...options.params})
    }

    // Stays on raw axios: no matching endpoint exposed by the generated SDK.
    const filePreview = (options: { executionId: string; path: string; maxRows?: number; encoding?: string }) => {
        return axios.get<FilePreview>(`${apiUrl()}/executions/${options.executionId}/file/preview`, {
            params: options,
        }).then(response => normalizeFilePreview({...response.data}))
    }

    // Fetches the complete, untruncated file as text. Unlike filePreview (which
    // caps rows and bytes for the RAW/TEXT viewer), this returns the whole file
    // so callers such as the HTML iframe preview can render a valid document.
    // The /file endpoint sets Content-Disposition: attachment, but that only
    // affects browser navigation — an XHR reads the body normally, and the
    // shared client attaches auth automatically.
    const fileContent = (options: { executionId: string; path: string }): Promise<string> => {
        return axios.get<string>(`${apiUrl()}/executions/${options.executionId}/file`, {
            params: {path: options.path},
            responseType: "text",
            transformResponse: [(data: string) => data],
        }).then(response => response.data)
    }

    const setLabels = (options: { executionId: string; labels: Label[] }) => {
        return ExecutionsAPI.setLabelsOnTerminatedExecution({executionId: options.executionId, body: options.labels})
    }

    const querySetLabels = (options: { data: Label[]; params: FilterQuery }) => {
        return ExecutionsAPI.setLabelsOnTerminatedExecutionsByQuery({filters: routeQueryToQueryFilters(options.params), body: options.data})
    }

    const bulkSetLabels = (options: { executionsId: string[]; executionLabels: Label[] }) => {
        return ExecutionsAPI.setLabelsOnTerminatedExecutionsByIds(options)
    }

    const unqueue = (options: { id: string; state: string }) => {
        return ExecutionsAPI.unqueueExecution({executionId: options.id, state: options.state as Parameters<typeof ExecutionsAPI.unqueueExecution>[0]["state"]}) as unknown as Promise<Execution>
    }

    const bulkUnqueueExecution = (options: { executionsId: string[]; newStatus: StateType }) => {
        return ExecutionsAPI.unqueueExecutionsByIds({body: options.executionsId, state: options.newStatus})
    }

    const queryUnqueueExecution = (options: FilterQuery & { newStatus: StateType }) => {
        return ExecutionsAPI.unqueueExecutionsByQuery({
            filters: routeQueryToQueryFilters(options),
            newState: options.newStatus,
        })
    }

    const forceRun = (options: { id: string }) => {
        return ExecutionsAPI.forceRunExecution({executionId: options.id}) as unknown as Promise<Execution>
    }

    const bulkForceRunExecution = (options: { executionsId: string[] }) => {
        return ExecutionsAPI.forceRunByIds({body: options.executionsId})
    }

    const queryForceRunExecution = (options: FilterQuery) => {
        return ExecutionsAPI.forceRunExecutionsByQuery({filters: routeQueryToQueryFilters(options)})
    }

    const loadFlowForExecution = (options: { namespace: string; flowId: string; revision?: number, store: boolean }) => {
        return ExecutionsAPI.flowFromExecution({namespace: options.namespace, flowId: options.flowId, revision: options.revision})
            .then(data => {
                if (options.store) {
                    flow.value = data
                }
                return data
            })
    }

    const loadFlowForExecutionByExecutionId = (options: { id: string, revision?: string }) => {
        return ExecutionsAPI.flowFromExecutionById({executionId: options.id})
            .then(data => {
                flow.value = data
                return data
            })
    }

    const fetchGraph = (options: GraphOptions): Promise<FlowGraph> => {
        return ExecutionsAPI.executionFlowGraph({executionId: options.id, subflows: options.params?.subflows}, {withCredentials: true}) as unknown as Promise<FlowGraph>
    }

    function loadGraph(options: GraphOptions) {
        return fetchGraph(options).then(graph => {
            // force refresh - Create a new object reference to trigger reactivity
            flowGraph.value = Object.assign({}, graph)
        })
    }

    function isUnused(nodeByUid: Record<string, FlowGraphNode>, nodeUid: string): boolean {
            const nodeToCheck = nodeByUid[nodeUid]

            if(!nodeToCheck) {
                return false
            }

            if(!nodeToCheck.task) {
                // check if parent is unused (current node is probably a cluster root or end)
                const splitUid = nodeToCheck.uid.split(".")
                splitUid.pop()
                return isUnused(nodeByUid, splitUid.join("."))
            }

            if (!nodeToCheck.executionId) {
                return true
            }

            const nodeExecution = nodeToCheck.executionId === execution.value?.id ? execution.value
                : Object.values(subflowsExecutions.value).filter(exec => exec.id === nodeToCheck.executionId)?.[0]

            if (!nodeExecution) {
                return true
            }

            return !nodeExecution.taskRunList?.some(taskRun => taskRun.taskId === nodeToCheck.task?.id)

        }

    const loadAugmentedGraph = async (options: GraphOptions) => {
        const params = options.params ? options.params : {}
        const graph = await fetchGraph({id: options.id, params})
        // Augment the graph with additional properties

        const subflowPaths = graph.clusters
            ?.map(c => c.cluster)
            ?.filter(cluster => cluster.type.endsWith("SubflowGraphCluster"))
            ?.map(cluster => cluster.uid.replace(CLUSTER_PREFIX, ""))
            ?? []
        const nodeByUid: Record<string, FlowGraphNode> = {}

        graph.nodes
            // lowest depth first to be available in nodeByUid map for child-to-parent unused check
            .sort((a, b) => a.uid.length - b.uid.length)
            .forEach(node => {
                nodeByUid[node.uid] = node

                const parentSubflow = subflowPaths.filter(subflowPath => node.uid.startsWith(subflowPath + "."))
                    .sort((a, b) => b.length - a.length)?.[0]

                if(parentSubflow) {
                    if(parentSubflow in subflowsExecutions.value) {
                        node.executionId = subflowsExecutions.value[parentSubflow]?.id
                    }

                    return
                }

                node.executionId = options.id

                // reduce opacity for cluster root & end
                if(!node.task && isUnused(nodeByUid, node.uid)) {
                    node.unused = true
                }
            })

        graph.edges
            // keep only unused (or skipped) paths
            .filter(edge => {
                return isUnused(nodeByUid, edge.target) || isUnused(nodeByUid, edge.source)
            }).forEach(edge => edge.unused = true)

        // force refresh - Create a new object reference to trigger reactivity
        flowGraph.value = Object.assign({}, graph)

        return graph
    }

    const loadNamespaces = () => {
        return ExecutionsAPI.listExecutableDistinctNamespaces()
            .then(data => {
                namespaces.value = data
            })
    }

    const loadFlowsExecutable = (options: { namespace: string }) => {
        return ExecutionsAPI.listFlowExecutionsByNamespace({namespace: options.namespace})
            .then(data => {
                flowsExecutable.value = data
            })
    }

    const loadLatestExecutions = (options: { flowFilters: ExecutionRepositoryInterfaceFlowFilter[] }) => {
        return ExecutionsAPI.latestExecutions({body: options.flowFilters})
    }

    // mutations
    const addSubflowExecution = (params: { subflow: string; execution: Execution }) => {
        subflowsExecutions.value[params.subflow] = params.execution
    }

    const removeSubflowExecution = (subflow: string) => {
        delete subflowsExecutions.value[subflow]
    }

    const addProgressEvent = (event: {taskId: string; taskRunId: string; step: string; timestamp: string}) => {
        // Overwrite (not skip) on a matching (taskRunId, step): a retried task reuses the same
        // taskRunId, so a later attempt re-emitting the same step must replace the stale value
        // from an earlier attempt, not be dropped. Idempotent for genuine SSE reconnect replay
        // since that resends the identical timestamp.
        //
        // Reassign the array rather than push/splice in place: consumers watching this ref
        // shallowly (e.g. to know when to re-render a topology node) only see a change on
        // reference reassignment, not on in-place mutation.
        const existingIndex = progressEvents.value.findIndex(e => e.taskRunId === event.taskRunId && e.step === event.step)
        if (existingIndex === -1) {
            progressEvents.value = [...progressEvents.value, event]
        } else {
            progressEvents.value = progressEvents.value.map((e, i) => i === existingIndex ? event : e)
        }
    }

    const resetLogs = () => {
        logs.value = []
    }

    const appendLogs = (entries: LogEntry[]) => {
        logs.value = logs.value.concat(entries)
    }

    const appendFollowedLogs = (entry: LogEntry) => {
        logs.value = [...logs.value, entry]
    }

    const getFlowExecutions = ({namespace, flowId}: { namespace: string; flowId: string }) => {
        return ExecutionsAPI.searchExecutionsByFlowId({namespace, flowId}).then(data => {
            executions.value = data.results as unknown as Execution[]
            total.value = data.total ?? 0
            return data
        })
    }

    // Stays on raw axios: CSV blob download, not a clean typed JSON call.
    const exportExecutionsAsCSV = async (params: FilterQuery) => {
        const response = await axios.get<string>(
            `${apiUrl()}/executions/export/by-query/csv`,
            {params, responseType: "text", headers: {Accept: "text/csv"}},
        )
        const url = window.URL.createObjectURL(new Blob([response.data]))
        const link = document.createElement("a")
        link.href = url
        link.setAttribute("download", "executions.csv")
        document.body.appendChild(link)
        link.click()
        link.remove()
        window.URL.revokeObjectURL(url)
    }

    return {
        // State
        executions,
        execution,
        total,
        logs,
        subflowsExecutions,
        progressEvents,
        flow,
        flowGraph,
        namespaces,
        flowsExecutable,
        // Actions
        restartExecution,
        bulkRestartExecution,
        queryRestartExecution,
        bulkResumeExecution,
        queryResumeExecution,
        bulkReplayExecution,
        bulkChangeExecutionStatus,
        queryReplayExecution,
        queryChangeExecutionStatus,
        replayExecution,
        replayExecutionWithInputs,
        changeExecutionStatus,
        changeStatus,
        interrupt,
        waitForStateChange,
        kill,
        bulkKill,
        queryKill,
        resume,
        resumeFromBreakpoint,
        validateResume,
        pause,
        bulkPauseExecution,
        queryPauseExecution,
        loadExecution,
        findExecutions,
        findDistinctFieldValues,
        validateExecution,
        triggerExecution,
        deleteExecution,
        bulkDeleteExecution,
        queryDeleteExecution,
        closeSSE,
        subscribeToExecution,
        followExecution,
        followExecutionDependencies,
        followLogs,
        loadLogs,
        downloadLogs,
        downloadLogsFile,
        deleteLogs,
        filePreview,
        fileContent,
        setLabels,
        querySetLabels,
        bulkSetLabels,
        unqueue,
        bulkUnqueueExecution,
        queryUnqueueExecution,
        forceRun,
        bulkForceRunExecution,
        queryForceRunExecution,
        loadFlowForExecution,
        loadFlowForExecutionByExecutionId,
        loadGraph,
        loadAugmentedGraph,
        loadNamespaces,
        loadFlowsExecutable,
        loadLatestExecutions,
        addSubflowExecution,
        removeSubflowExecution,
        addProgressEvent,
        resetLogs,
        appendLogs,
        appendFollowedLogs,
        getFlowExecutions,
        exportExecutionsAsCSV,
    }
})
