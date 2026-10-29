import {computed, ref, watch, type Ref} from "vue"
import {defineStore} from "pinia"
import {useUrlSearchParams} from "@vueuse/core"
import * as PlaygroundAPI from "@kestra-io/kestra-sdk/playground"
import type {PlaygroundPlan} from "@kestra-io/kestra-sdk"
import {Execution, useExecutionsStore} from "./executions"
import {normalize} from "../utils/inputs"
import {useRoute, useRouter} from "vue-router"
import {State, durationUtils} from "@kestra-io/design-system"
import {Flow, isSuccessfulFlowSaveOutcome, useFlowStore} from "./flow"
import type {FlowForExecution} from "@kestra-io/kestra-sdk"
import {useFileExplorerStore} from "./fileExplorer"
import type {KestraHttpError} from "../utils/kestraHttp"

import {storageKeys} from "../utils/constants"
import {routeQueryToQueryFilters} from "../utils/queryFilters"

/** The execution store's flow requires the flags and tasks that the flow store leaves optional. */
const forExecution = (flow: Flow): FlowForExecution => ({
    ...flow,
    disabled: flow.disabled ?? false,
    draft: flow.draft ?? false,
    deleted: flow.deleted ?? false,
    tasks: flow.tasks ?? [],
})

const CLIENT_ID_KEY = "playgroundClientId"
const DEFAULT_REPORT_EVERY_MS = 20_000
const SESSION_SIZE = 10

let tabClientId: string | undefined

/** One id per browser tab, kept across reloads so a reload reports as the same panel. */
function clientId(): string {
    if (tabClientId) return tabClientId
    try {
        tabClientId = sessionStorage.getItem(CLIENT_ID_KEY) ?? crypto.randomUUID()
        sessionStorage.setItem(CLIENT_ID_KEY, tabClientId)
    } catch {
        tabClientId ??= crypto.randomUUID()
    }
    return tabClientId
}

/** What the next run builds on: the latest run, nothing, or one run by id. */
export type BuildOn = "LATEST" | "NOTHING" | string

export const usePlaygroundStore = defineStore("playground", () => {

    const flowStore = useFlowStore()
    const executionsStore = useExecutionsStore()
    const params = useUrlSearchParams("history", {
        removeFalsyValues: true,
    })

    const enabled = ref<boolean>(params.playground === "on" && localStorage.getItem(storageKeys.EDITOR_PLAYGROUND) !== "false")
    const route = useRoute()
    const router = useRouter()

    const executions = ref([]) as Ref<Execution[]>
    const buildOn = ref<BuildOn>("LATEST")
    const latestExecution = computed(() => executions.value[0])
    const executionState = computed(() => latestExecution.value?.state.current)

    function flowRef() {
        return {namespace: flowStore.flow?.namespace ?? "", id: flowStore.flow?.id ?? ""}
    }

    function navigateToEdit(runUntilTaskId?: string, runDownstreamTasks?: boolean) {
        const flowParsed = flowStore.flow
        return router.push({
            name: "flows/update/edit",
            params: {
                id: flowParsed?.id,
                namespace: flowParsed?.namespace,
                tenant: route.params.tenant,
            },
            query: {
                playground: "on",
                runUntilTaskId,
                runDownstreamTasks: runDownstreamTasks ? "true" : undefined,
            },
        })
    }

    /** Loads the session kept on the server: the caller's latest runs of this flow, the newest first. */
    async function loadSession() {
        if (!flowStore.flow?.id || flowStore.isCreating) return
        const page = await PlaygroundAPI.listPlaygroundRuns({...flowRef(), page: 1, size: SESSION_SIZE})
        executions.value = (page.results ?? []) as unknown as Execution[]
        if (latestExecution.value) {
            await showReusedTaskRuns(latestExecution.value)
        }
    }

    /** Starts the next run fresh instead of building on the latest one, without deleting anything. */
    function clearExecutions() {
        executions.value = []
        buildOn.value = "NOTHING"
        executionsStore.execution = undefined
        executionsStore.clearReusedTaskRuns()
    }

    function addExecution(execution: Execution) {
        executions.value = [execution, ...executions.value.filter(e => e.id !== execution.id)].slice(0, SESSION_SIZE)
    }

    function triggerExecution(flow: Flow, breakpoints?: string[], customFormData?: Record<string, unknown>) {
        let formData = customFormData
        if (!formData) {
            formData = {}
            const lastInputs = latestExecution.value?.inputs || {}

            for (const input of (flow.inputs || [])) {
                const {type, defaults, id} = input
                const valueToUse = lastInputs[id] !== undefined ? lastInputs[id] : defaults

                // for dates and times, no need to normalize the value
                // https://github.com/kestra-io/kestra/issues/10576
                const safeDef = (type === "DATE" || type === "TIME")
                    ? valueToUse
                    : normalize(type, valueToUse)

                if(safeDef !== undefined) {
                    formData[id] = safeDef
                }
            }
        }

        return executionsStore.triggerExecution({
            id: flow.id,
            namespace: flow.namespace,
            formData,
            kind: "PLAYGROUND",
            breakpoints,
            // Explicit revision so drafts run too - the backend otherwise resolves the latest published one.
            revision: flow.revision,
        })
    }

    async function replay(plan: PlaygroundPlan): Promise<Execution | undefined> {
        try {
            return await executionsStore.replayExecution({
                executionId: plan.buildOnExecutionId!,
                taskRunId: plan.replayFromTaskRunId!,
                revision: flowStore.flow?.revision,
                breakpoints: plan.breakpoints,
            })
        } catch (error: unknown) {
            // the run to build on can be purged or ended by another tab between the plan and the replay
            if ((error as KestraHttpError | undefined)?.response?.status !== 404) {
                throw error
            }
            return undefined
        }
    }

    const nonFinalStates: string[] = [
        State.KILLING,
        State.RUNNING,
        State.RESTARTED,
        State.CREATED,
    ]

    const readyToStartPure = computed(()=>{
        const executionReady = !latestExecution.value || !nonFinalStates.includes(executionState.value ?? "")
        const flowValid = !(flowStore.haveChange && flowStore.flowErrors)
        return executionReady && flowValid
    })

    const executionSettled = ref(readyToStartPure.value)
    watch(readyToStartPure, (newValue) => {
        if(newValue) {
            setTimeout(() => {
                executionSettled.value = newValue
            }, 1000)
        } else {
            executionSettled.value = newValue
        }
    })

    const starting = ref(false)
    const readyToStart = computed(() => !starting.value && readyToStartPure.value && executionSettled.value)

    const showInputPrompt = ref(false)
    const actionOptions = ref<{taskId?: string, runDownstreamTasks?: boolean}>()

    // Ensure Files panel reflects changes after Playground executions (e.g., Namespace/Tenant sync tasks)
    // When an execution transitions from a non-final state to a final state, refresh the files tree
    // @see https://github.com/kestra-io/plugin-git/issues/188
    const fileExplorerStore = useFileExplorerStore()
    watch(() => executionState.value, (newState, oldState) => {
        if (!latestExecution.value) return

        const wasRunning = oldState ? nonFinalStates.includes(oldState) : false
        const isFinalNow = newState ? !nonFinalStates.includes(newState) : false

        if (wasRunning && isFinalNow) {
            // Trigger a refresh of the namespace files tree in the file explorer
            fileExplorerStore.loadNodes()
        }
    })

    function runFromQuery(){
        if(route.query.runUntilTaskId) {
            const {runUntilTaskId, runDownstreamTasks} = route.query
            runUntilTask(runUntilTaskId.toString(), Boolean(runDownstreamTasks))

            // remove the query parameters to avoid running the same task again
            router.replace({
                name: route.name,
                params: route.params,
                query: {
                    ...route.query,
                    runUntilTaskId: undefined,
                    runDownstreamTasks: undefined,  // remove the query parameter
                },
            })
        }
    }

    async function runUntilTask(taskId?: string, runDownstreamTasks = false, customFormData?: Record<string, unknown>) {
        if(readyToStart.value === false) {
            console.warn("Playground is not ready to start, latest execution is still in progress")
            return
        }
        if (flowStore.haveChange && flowStore.flowErrors) {
            return
        }
        if (flowStore.isCreating) {
            starting.value = true
            let outcome
            try {
                outcome = await flowStore.saveAsDraft()
            } finally {
                starting.value = false
            }
            if (isSuccessfulFlowSaveOutcome(outcome)) {
                await navigateToEdit(taskId, runDownstreamTasks)
                enabled.value = true
            }
            return
        }
        starting.value = true
        try {
            await startRun(taskId, runDownstreamTasks, customFormData)
        } finally {
            starting.value = false
        }
    }

    function missingRequiredInput(flow: Flow) {
        const lastInputs = latestExecution.value?.inputs ?? {}
        return (flow.inputs || []).some(input => input.required && input.defaults === undefined && lastInputs[input.id] === undefined)
    }

    function promptForInputs(flow: Flow, taskId?: string, runDownstreamTasks?: boolean) {
        actionOptions.value = {taskId, runDownstreamTasks}
        executionsStore.flow = forExecution(flow)
        showInputPrompt.value = true
    }

    async function startRun(taskId?: string, runDownstreamTasks = false, customFormData?: Record<string, unknown>) {
        await flowStore.saveAsDraft()
        const flow = flowStore.flow
        if (!flow) {
            console.warn("Flow is not defined, cannot trigger execution")
            return
        }

        const plan = await PlaygroundAPI.planPlaygroundRun({
            ...flowRef(),
            revision: flow.revision ?? 1,
            taskId,
            mode: runDownstreamTasks ? "FROM" : "UP_TO",
            buildOn: buildOn.value,
        })
        const willReplay = plan.freshReason === "NONE"

        if (!willReplay && !customFormData && missingRequiredInput(flow)) {
            promptForInputs(flow, taskId, runDownstreamTasks)
            return
        }

        let execution: Execution | undefined = undefined
        try {
            execution = willReplay ? await replay(plan) : undefined
            execution ??= await triggerExecution(flow, plan.breakpoints, customFormData)
        } catch (error: unknown) {
            if ((error as KestraHttpError | undefined)?.response?.status === 422 && !customFormData && flow.inputs?.length) {
                promptForInputs(flow, taskId, runDownstreamTasks)
                return
            }
            throw error
        }

        buildOn.value = "LATEST"
        executionsStore.execution = execution
        if (execution) {
            addExecution(execution)
            await showReusedTaskRuns(execution)
        }
    }

    /** Marks the task runs a run reused and carries their logs over: those logs stay with the run that first produced them. */
    async function showReusedTaskRuns(execution: Execution) {
        const reused = await PlaygroundAPI.listReusedTaskRuns({...flowRef(), executionId: execution.id})
            .then(response => response.taskRuns)
            .catch(() => ({}))
        const producers = Object.entries(reused)
        if (!producers.length) return

        const producerRuns = [...new Set(producers.map(([, producer]) => producer.executionId))]
        const filters = routeQueryToQueryFilters({"filters[kind][IN]": "PLAYGROUND"})
        // The run is already going, so a failed fetch only costs the carried-over logs, not the run.
        const logsByRun = await Promise.all(producerRuns.map(executionId =>
            executionsStore.loadLogs({executionId, filters, store: false}).catch(() => []),
        ))
        const reusedIdByProducer = new Map(producers.map(([reusedId, producer]) => [producer.taskRunId, reusedId]))
        executionsStore.setReusedTaskRuns(execution.id, producers.map(([reusedId]) => reusedId), logsByRun.flat().flatMap(log => {
            const taskRunId = log.taskRunId ? reusedIdByProducer.get(log.taskRunId) : undefined
            return taskRunId ? [{...log, executionId: execution.id, taskRunId}] : []
        }))
    }

    function updateExecution(execution: Execution) {
        const index = executions.value.findIndex(e => e.id === execution.id)
        if (index !== -1) {
            executions.value[index] = execution
        }
    }

    // when following an execution, the status changes after creation
    watch(() => executionsStore.execution, (newValue) => {
        if (newValue) {
            updateExecution(newValue)
        }
    })

    let heartbeat: ReturnType<typeof setTimeout> | undefined
    let reportingRunId: string | undefined
    let leavingEditor = false
    const watchedRunId = computed(() => {
        const latest = latestExecution.value
        return enabled.value && latest && !State.isTerminated(latest.state.current) ? latest.id : undefined
    })

    function stopReporting() {
        clearTimeout(heartbeat)
        heartbeat = undefined
    }

    /** Tells the server this panel still shows the run, so it keeps it alive at its breakpoint. */
    async function report(executionId: string) {
        let delay = DEFAULT_REPORT_EVERY_MS
        try {
            const response = await PlaygroundAPI.reportPlaygroundClient({...flowRef(), executionId, clientId: clientId()})
            delay = (durationUtils.duration(response.reportEvery) * 1000) || DEFAULT_REPORT_EVERY_MS
        } catch {
            // a missed report is covered by the next ones: the server only ends a run after several
        }
        if (reportingRunId === executionId) {
            heartbeat = setTimeout(() => report(executionId), delay)
        }
    }

    watch(watchedRunId, (executionId) => {
        stopReporting()
        reportingRunId = executionId
        if (executionId) report(executionId)
    }, {immediate: true})

    // sync, so the run this panel reported is still known when it closes
    watch(enabled, (newValue) => {
        params.playground = newValue ? "on" : ""
        if (!newValue && !leavingEditor && reportingRunId) {
            PlaygroundAPI.releasePlaygroundClient({...flowRef(), executionId: reportingRunId, clientId: clientId()}).catch(() => undefined)
        }
    }, {flush: "sync"})

    /** Leaving the editor stops reporting without ending the run: like a reload, it lets the server's timeout decide. */
    function leaveEditor() {
        leavingEditor = true
        enabled.value = false
        leavingEditor = false
        stopReporting()
        executions.value = []
        buildOn.value = "LATEST"
        executionsStore.execution = undefined
        executionsStore.clearReusedTaskRuns()
    }

    const dropdownOpened = ref<boolean>(false)

    return {
        enabled,
        dropdownOpened,
        showInputPrompt,
        actionOptions,
        readyToStart,
        executions,
        latestExecution,
        buildOn,
        loadSession,
        clearExecutions,
        leaveEditor,
        runUntilTask,
        runFromQuery,
        executionState,
    }
})
