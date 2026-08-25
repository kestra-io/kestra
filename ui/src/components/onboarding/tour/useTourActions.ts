import {useRoute, useRouter} from "vue-router"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import {State} from "@kestra-io/design-system"

import {useFlowStore} from "../../../stores/flow"
import {useExecutionsStore} from "../../../stores/executions"
import {usePluginsStore} from "../../../stores/plugins"
import {useProductTourStore} from "../../../stores/productTour"
import {FLOW_PARENT_ROUTE} from "../../flows/flowTabs"
import {EXECUTION_PARENT_ROUTE} from "../../executions/executionTabs"
import {sendWebhookTestEvent} from "../../flows/testEvent"
import {TourSceneError} from "./tourScenes"
import {
    TOUR_FLOW_ID,
    TOUR_MANUAL_LABEL,
    TOUR_NAMESPACE,
    TOUR_REPORT_FLOW_ID,
    TOUR_REPORT_FLOW,
    tourFlowSource,
} from "./tourFlows"

const ONBOARDING_FLOW_PRESET_KEY = "kestra.onboarding.flowPreset"

const FLOW_TAB = {
    edit: `${FLOW_PARENT_ROUTE}/edit`,
    revisions: `${FLOW_PARENT_ROUTE}/revisions`,
    triggers: `${FLOW_PARENT_ROUTE}/triggers`,
} as const

// Not State.isRunning: QUEUED/RETRYING/RESTARTED are non-running yet not a final outcome.
const TERMINAL_STATES: readonly string[] = [State.SUCCESS, State.WARNING, State.FAILED, State.KILLED, State.CANCELLED]

const randomWebhookKey = () => {
    const random = Math.random().toString(36).slice(2, 10)
    return `order-events-${random}`
}

export function useTourActions() {
    const route = useRoute()
    const router = useRouter()
    const flowStore = useFlowStore()
    const executionsStore = useExecutionsStore()
    const pluginsStore = usePluginsStore()
    const tourStore = useProductTourStore()

    const tenant = () => route.params.tenant as string | undefined

    const tour = () => tourStore.state.tour

    const wait = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))

    const tourFlowExists = async (id: string = TOUR_FLOW_ID) => {
        try {
            const flows = await FlowsAPI.listFlowsByNamespace({namespace: TOUR_NAMESPACE})
            return (flows ?? []).some((flow: any) => flow?.id === id)
        } catch {
            return false
        }
    }

    const generateFlow = async () => {
        if (await tourFlowExists()) {
            await openFlowEditor()
            return
        }

        sessionStorage.setItem(ONBOARDING_FLOW_PRESET_KEY, tourFlowSource.generated())
        await router.push({
            name: "flows/create",
            query: {onboardingPreset: "true"},
            params: {tenant: tenant()},
        })
    }

    const openCopilot = async () => {
        await router.push({name: "ai", params: {tenant: tenant()}})
    }

    const openFlowEditor = async (id: string = TOUR_FLOW_ID) => {
        await router.push({
            name: FLOW_TAB.edit,
            params: {namespace: TOUR_NAMESPACE, id, tenant: tenant()},
        })
    }

    const editorPanelsKey = () => `el-fl-${TOUR_NAMESPACE}-${TOUR_FLOW_ID}`

    interface StoredPanel {
        tabs: string[];
        activeTab?: string;
        size: number;
    }

    const readEditorPanels = (): StoredPanel[] => {
        try {
            const stored = JSON.parse(localStorage.getItem(editorPanelsKey()) ?? "null")
            return Array.isArray(stored) ? stored : []
        } catch {
            return []
        }
    }

    const rememberEditorPanels = () => {
        if (tour().editorPanelsRemembered) {
            return
        }
        tourStore.setTourState({
            editorPanelsBackup: localStorage.getItem(editorPanelsKey()),
            editorPanelsRemembered: true,
        })
    }

    const showDocsPanel = () => {
        rememberEditorPanels()

        const panels = readEditorPanels()
        if (panels.some((panel) => panel.tabs?.includes("doc"))) {
            return
        }

        const withDocs: StoredPanel[] = [
            ...(panels.length ? panels : [{tabs: ["code"], activeTab: "code", size: 100}]),
            {tabs: ["doc"], activeTab: "doc", size: 100},
        ]
        const layout = JSON.stringify(
            withDocs.map((panel) => ({...panel, size: 100 / withDocs.length})),
        )

        localStorage.setItem(editorPanelsKey(), layout)
        // Synthetic event so an already-open editor (this same tab raises none) picks up the change.
        window.dispatchEvent(new StorageEvent("storage", {
            key: editorPanelsKey(),
            newValue: layout,
            storageArea: localStorage,
        }))
    }

    const restoreEditorPanels = () => {
        if (!tour().editorPanelsRemembered) {
            return
        }
        const backup = tour().editorPanelsBackup
        if (backup === null) {
            localStorage.removeItem(editorPanelsKey())
        } else {
            localStorage.setItem(editorPanelsKey(), backup)
        }
        tourStore.setTourState({editorPanelsBackup: null, editorPanelsRemembered: false})
    }

    const showTaskDocs = async (cls: string) => {
        if (!pluginsStore.plugins?.length) {
            await pluginsStore.listWithSubgroup({includeDeprecated: false})
        }
        await pluginsStore.updateDocumentation({cls})
    }

    const openRevisionDiff = async (highlight?: string) => {
        const revision = (await latestRevision()) ?? 1
        await flowStore.loadRevisions({namespace: TOUR_NAMESPACE, id: TOUR_FLOW_ID})
        await router.push({
            name: FLOW_TAB.revisions,
            params: {namespace: TOUR_NAMESPACE, id: TOUR_FLOW_ID, tenant: tenant()},
            query: {
                revisionLeft: String(Math.max(1, revision - 1)),
                revisionRight: String(revision),
                ...(highlight ? {revisionHighlight: highlight} : {}),
            },
        })
    }

    const openTriggersTab = async () => {
        await router.push({
            name: FLOW_TAB.triggers,
            params: {namespace: TOUR_NAMESPACE, id: TOUR_FLOW_ID, tenant: tenant()},
        })
    }

    const openExecution = async (
        executionId: string,
        flowId: string = TOUR_FLOW_ID,
        query: Record<string, string> = {},
        tab: "gantt" | "outputs" = "gantt",
    ) => {
        await router.push({
            name: `${EXECUTION_PARENT_ROUTE}/${tab}`,
            params: {namespace: TOUR_NAMESPACE, flowId, id: executionId, tenant: tenant()},
            query,
        })
    }

    const openExecutionsList = async () => {
        await router.push({
            name: "executions/list",
            params: {tenant: tenant()},
            // Bracket format: flat filter params are dropped on their way to the API (and rejected by it now -
            // kestra-io/kestra-ee#10326), so the tour never actually scoped this list to its own namespace.
            query: {
                "filters[namespace][EQUALS]": TOUR_NAMESPACE,
                "filters[scope][EQUALS]": "USER",
            },
        })
    }

    const waitFor = async (condition: () => boolean, timeoutMs = 15_000) => {
        const startedAt = Date.now()
        while (!condition()) {
            if (Date.now() - startedAt > timeoutMs) {
                return false
            }
            await wait(100)
        }
        return true
    }

    const writeSource = async (source: string) => {
        await waitFor(() => Boolean(flowStore.flow))

        flowStore.flowYaml = source
        await flowStore.onEdit({source})
    }

    const saveSource = async (source: string) => {
        if (await tourFlowExists()) {
            await flowStore.saveFlow({flow: source})
        } else {
            await FlowsAPI.createFlow({
                body: source,
                showMessageOnError: false,
            } as Parameters<typeof FlowsAPI.createFlow>[0])
            await flowStore.loadFlow({namespace: TOUR_NAMESPACE, id: TOUR_FLOW_ID})
        }
        await flowStore.initYamlSource()
        return flowStore.flow
    }

    const applySource = async (source: string) => {
        if (route.name !== "flows/create") {
            return saveSource(source)
        }

        await writeSource(source)
        const outcome = await flowStore.saveAll()
        if (outcome === "blocked") {
            throw new TourSceneError("onboarding.tour.errors.save_flow", {
                reason: flowStore.flowErrors?.join(" ") ?? "",
            })
        }
        await openFlowEditor()
        return flowStore.flow
    }

    const openEditorWith = async (source: string) => {
        await saveSource(source)
        await openFlowEditor()
        await flowStore.initYamlSource()
    }

    const latestRevision = async (id: string = TOUR_FLOW_ID) => {
        const flow = await flowStore.loadFlow({namespace: TOUR_NAMESPACE, id, store: false})
        return flow?.revision as number | undefined
    }

    const waitForExecution = async (executionId: string, timeoutMs = 180_000) => {
        const startedAt = Date.now()
        for (;;) {
            const execution = await executionsStore.loadExecution({id: executionId})
            const current = execution?.state?.current
            if (current && TERMINAL_STATES.includes(current)) {
                return execution
            }
            if (Date.now() - startedAt > timeoutMs) {
                return execution
            }
            await wait(1000)
        }
    }

    const executeFlow = async (flowId: string = TOUR_FLOW_ID) => {
        const execution = await executionsStore.triggerExecution({
            namespace: TOUR_NAMESPACE,
            id: flowId,
            kind: "NORMAL",
            formData: {},
            labels: [TOUR_MANUAL_LABEL],
        })
        tourStore.setTourState({lastExecutionId: execution.id})
        await openExecution(execution.id, flowId)
        return waitForExecution(execution.id)
    }

    // Replay, not restart: only replay copies successful task-run outputs, which the fixed message needs.
    const replayFromFailedTask = async () => {
        const executionId = tour().failedExecutionId ?? tour().lastExecutionId
        if (!executionId) {
            throw new TourSceneError("onboarding.tour.errors.no_execution_to_replay")
        }

        const execution = await executionsStore.loadExecution({id: executionId})
        const failedTaskRun = (execution?.taskRunList ?? []).find(
            (taskRun: any) => taskRun?.state?.current === "FAILED",
        )
        const revision = await latestRevision()

        const replayed = await executionsStore.replayExecution({
            executionId,
            taskRunId: failedTaskRun?.id,
            revision,
        })

        const replayedId = replayed?.id ?? executionId
        tourStore.setTourState({restartedExecutionId: replayedId, lastExecutionId: replayedId})
        await openExecution(replayedId)
        return waitForExecution(replayedId)
    }

    const ensureWebhookKey = () => {
        const existing = tour().webhookKey
        if (existing) {
            return existing
        }
        const key = randomWebhookKey()
        tourStore.setTourState({webhookKey: key})
        return key
    }

    const sendTestEvent = async (payload: string, headers: Record<string, string> = {}) => {
        const result = await sendWebhookTestEvent({
            namespace: TOUR_NAMESPACE,
            flowId: TOUR_FLOW_ID,
            key: ensureWebhookKey(),
            payload,
            headers,
        })

        if (result.executionId) {
            tourStore.setTourState({
                eventExecutionId: result.executionId,
                lastExecutionId: result.executionId,
            })
        }

        return result
    }

    const findEventExecution = async (since: string) => {
        try {
            const response = await executionsStore.findExecutions({
                commit: false,
                "filters[namespace][EQUALS]": TOUR_NAMESPACE,
                "filters[flowId][EQUALS]": TOUR_FLOW_ID,
                "filters[startDate][GREATER_THAN]": since,
                size: 1,
            })
            return response?.results?.[0]?.id as string | undefined
        } catch {
            return undefined
        }
    }

    const waitForReportExecution = async (since: string, timeoutMs = 60_000) => {
        const startedAt = Date.now()
        while (Date.now() - startedAt < timeoutMs) {
            try {
                const total = await executionsStore.findExecutions({
                    commit: false,
                    onlyTotal: true,
                    "filters[namespace][EQUALS]": TOUR_NAMESPACE,
                    "filters[flowId][EQUALS]": TOUR_REPORT_FLOW_ID,
                    "filters[startDate][GREATER_THAN]": since,
                    size: 1,
                })
                if (Number(total) > 0) {
                    return true
                }
            } catch {
                // ignore transient failures, the loop retries
            }
            await wait(1500)
        }
        return false
    }

    const removeReportFlow = async () => {
        if (!(await tourFlowExists(TOUR_REPORT_FLOW_ID))) {
            return
        }
        try {
            await FlowsAPI.deleteFlow({namespace: TOUR_NAMESPACE, id: TOUR_REPORT_FLOW_ID})
        } catch {
            // ignore
        }
        tourStore.setTourState({reportFlowCreated: false})
    }

    const createReportFlow = async () => {
        if (await tourFlowExists(TOUR_REPORT_FLOW_ID)) {
            await flowStore.saveFlow({flow: TOUR_REPORT_FLOW})
        } else {
            await FlowsAPI.createFlow({
                body: TOUR_REPORT_FLOW,
                showMessageOnError: false,
            } as Parameters<typeof FlowsAPI.createFlow>[0])
        }
        tourStore.setTourState({reportFlowCreated: true})
        await openFlowEditor(TOUR_REPORT_FLOW_ID)
    }

    return {
        generateFlow,
        openCopilot,
        openFlowEditor,
        openRevisionDiff,
        showTaskDocs,
        showDocsPanel,
        restoreEditorPanels,
        tourFlowExists,
        openTriggersTab,
        openExecution,
        openExecutionsList,
        writeSource,
        saveSource,
        applySource,
        openEditorWith,
        executeFlow,
        replayFromFailedTask,
        latestRevision,
        waitForExecution,
        waitForReportExecution,
        findEventExecution,
        ensureWebhookKey,
        sendTestEvent,
        createReportFlow,
        removeReportFlow,
        wait,
    }
}

export type TourActions = ReturnType<typeof useTourActions>;
