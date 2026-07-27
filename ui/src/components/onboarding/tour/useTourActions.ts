import {useRoute, useRouter} from "vue-router"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"

import {useFlowStore} from "../../../stores/flow"
import {useExecutionsStore} from "../../../stores/executions"
import {usePluginsStore} from "../../../stores/plugins"
import {useProductTourStore} from "../../../stores/productTour"
import {FLOW_PARENT_ROUTE} from "../../flows/flowTabs"
import {EXECUTION_PARENT_ROUTE} from "../../executions/executionTabs"
import {sendWebhookTestEvent} from "../../flows/testEvent"
import {
    TOUR_FLOW_ID,
    TOUR_MANUAL_LABEL,
    TOUR_NAMESPACE,
    TOUR_REPORT_FLOW_ID,
    TOUR_REPORT_FLOW,
    tourFlowSource,
} from "./tourFlows"

const ONBOARDING_FLOW_PRESET_KEY = "kestra.onboarding.flowPreset"

/** The flow editor's tabs are child routes, so a tab is a route name rather than a param. */
const FLOW_TAB = {
    edit: `${FLOW_PARENT_ROUTE}/edit`,
    revisions: `${FLOW_PARENT_ROUTE}/revisions`,
    triggers: `${FLOW_PARENT_ROUTE}/triggers`,
} as const
const TERMINAL_STATES = ["SUCCESS", "WARNING", "FAILED", "KILLED", "CANCELLED"]

/** Kestra webhook keys are opaque strings; a random one keeps two instances from sharing a URL. */
const randomWebhookKey = () => {
    const random = Math.random().toString(36).slice(2, 10)
    return `order-events-${random}`
}

/**
 * Everything the tour does to the instance.
 *
 * Each function is a real API call through the regular stores, so what the user sees on screen is
 * their own flow, their own executions and their own trigger, not a simulation.
 */
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

    /**
     * Whether a previous run of the tour already created the flow on this instance.
     *
     * Listing the namespace rather than fetching the flow by id: a missing flow answers 404, and
     * that surfaces an error toast on screen before the tour has done anything.
     */
    const tourFlowExists = async (id: string = TOUR_FLOW_ID) => {
        try {
            const flows = await FlowsAPI.listFlowsByNamespace({namespace: TOUR_NAMESPACE})
            return (flows ?? []).some((flow: any) => flow?.id === id)
        } catch {
            return false
        }
    }

    /**
     * Copilot scene: hand the generated flow to the editor the same way the welcome page does.
     *
     * Someone taking the tour a second time already has the flow, so the editor is opened on it
     * instead of trying to create it again.
     */
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

    /**
     * The Copilot page, where the tour starts.
     *
     * The composer is left alone on purpose. An instance with no AI provider configured answers a
     * prompt with "No AI provider is configured", so the tour describes the example flow its own
     * button creates rather than inviting a request that may not work.
     */
    const openCopilot = async () => {
        await router.push({name: "ai", params: {tenant: tenant()}})
    }

    const openFlowEditor = async (id: string = TOUR_FLOW_ID) => {
        await router.push({
            name: FLOW_TAB.edit,
            params: {namespace: TOUR_NAMESPACE, id, tenant: tenant()},
        })
    }

    /** Where the flow editor keeps its panel layout, per flow. */
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

    /**
     * Keep the panel layout the user had, before the tour opens the Docs panel in it.
     *
     * Taken once per tour: whatever the layout looked like the first time the tour touched it is what
     * gets put back at the end.
     */
    const rememberEditorPanels = () => {
        if (tour().editorPanelsRemembered) {
            return
        }
        tourStore.setTourState({
            editorPanelsBackup: localStorage.getItem(editorPanelsKey()),
            editorPanelsRemembered: true,
        })
    }

    /**
     * Make sure the Docs panel is part of the flow editor's layout.
     *
     * Written to storage rather than clicked: the tour has no handle on the editor's panels, and the
     * editor reads this key while it mounts. The synthetic storage event is for an editor that is
     * already open, since a write from this same tab raises none of its own.
     */
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
        window.dispatchEvent(new StorageEvent("storage", {
            key: editorPanelsKey(),
            newValue: layout,
            storageArea: localStorage,
        }))
    }

    /** Put the layout back as the tour found it, or remove it if the user had none. */
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

    /**
     * Show a task's or trigger's documentation in the editor's Docs panel.
     *
     * The panel follows the cursor in the editor, which sits on the first line after the tour has
     * written the source, so what the card talks about is selected here instead. Same store call the
     * editor makes on a cursor move, so the panel is left in a state it can reach on its own.
     */
    const showTaskDocs = async (cls: string) => {
        if (!pluginsStore.plugins?.length) {
            await pluginsStore.listWithSubgroup({includeDeprecated: false})
        }
        await pluginsStore.updateDocumentation({cls})
    }

    /**
     * Open the revisions tab on the diff between the last two revisions.
     *
     * The pair is passed in the query rather than left to the default: the revisions the tab has in
     * the store are the ones it loaded before the tour saved, so the default pair would be one
     * revision behind. `highlight` is a line of the new revision to reveal, since a long flow opens
     * on its first lines and the change can be several screens down.
     */
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
            query: {namespace: TOUR_NAMESPACE, scope: "USER"},
        })
    }

    /** Poll a condition, used to let the editor finish mounting before writing into it. */
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

    /**
     * Put a source into the editor of a flow that does not exist yet, then validate it.
     *
     * Only used on the creation route: for a saved flow the source goes through the API first, which
     * avoids racing with the load the editor runs while it mounts.
     */
    const writeSource = async (source: string) => {
        // Right after the route change the editor briefly holds an empty source, and validating an
        // empty flow leaves a "flow must not be empty" constraint behind that would block the save.
        await waitFor(() => Boolean(flowStore.flow))

        flowStore.flowYaml = source
        await flowStore.onEdit({source})
    }

    /**
     * Save a source for an existing flow through the API, then point the editor at it.
     *
     * Writing into the editor and pressing its Save races with the load the editor runs while it
     * mounts, which silently reverts the edit. Saving first removes the race: whatever the editor
     * loads afterwards is already the new revision.
     */
    const saveSource = async (source: string) => {
        // Creating rather than updating when the flow is not there yet: the user may have walked away
        // from the creation page, and every scene from here on addresses the flow by id.
        if (await tourFlowExists()) {
            await flowStore.saveFlow({flow: source})
        } else {
            await FlowsAPI.createFlow({
                body: source,
                showMessageOnError: false,
            } as Parameters<typeof FlowsAPI.createFlow>[0])
            await flowStore.loadFlow({namespace: TOUR_NAMESPACE, id: TOUR_FLOW_ID})
        }
        // Re-seeds the editor from the flow that was just saved and revalidates it, which also
        // clears the "outdated revision" warning left by validating the previous one.
        await flowStore.initYamlSource()
        return flowStore.flow
    }

    /**
     * Write a source into the editor and save it.
     *
     * On the creation route this goes through the editor's own save, so the user sees their first
     * flow being created the regular way.
     */
    const applySource = async (source: string) => {
        if (route.name !== "flows/create") {
            return saveSource(source)
        }

        await writeSource(source)
        const outcome = await flowStore.saveAll()
        if (outcome === "blocked") {
            throw new Error(flowStore.flowErrors?.join(" ") ?? "The tour flow could not be saved")
        }
        // A creation redirects to the update route so later scenes can address the flow by id.
        await openFlowEditor()
        return flowStore.flow
    }

    /**
     * Leave the editor open on the tour flow with this source already saved in it, so a scene can
     * describe an edit that is on screen before the user presses anything.
     */
    const openEditorWith = async (source: string) => {
        await saveSource(source)
        await openFlowEditor()
        // Mounted editors keep the store's source, so re-seed it after the navigation as well.
        await flowStore.initYamlSource()
    }

    const latestRevision = async (id: string = TOUR_FLOW_ID) => {
        const flow = await flowStore.loadFlow({namespace: TOUR_NAMESPACE, id, store: false})
        return flow?.revision as number | undefined
    }

    /** Poll until the execution reaches a terminal state, then return it. */
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
        tourStore.recordExecution()
        await openExecution(execution.id, flowId)
        return waitForExecution(execution.id)
    }

    /**
     * Replay the failed execution from its failed task, against the fixed revision.
     *
     * Replay rather than restart: both reuse the task runs that already succeeded, but only replay
     * copies their outputs to the new execution, which the fixed Slack message needs. Restarting
     * with a revision creates a new execution id and leaves the copied task runs without outputs.
     */
    const replayFromFailedTask = async () => {
        const executionId = tour().failedExecutionId ?? tour().lastExecutionId
        if (!executionId) {
            throw new Error("No execution to replay")
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

    /**
     * Send the test event straight from the browser.
     *
     * A curl command would not work for everyone (Windows has no curl by default), and this also
     * keeps the user on the page while the execution appears.
     */
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

    /**
     * The most recent execution of the tour flow started after `since`, if there is one.
     *
     * Used while the trigger is on screen: the URL can be copied out of the editor and called with
     * curl or from another tab, and an execution created that way is the one the tour goes on with.
     */
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

    /**
     * Wait until the report flow has an execution newer than the given time.
     *
     * The Flow trigger only fires once the first flow succeeds, and the executions list does not
     * poll, so the tour waits for it here instead of leaving the user on a list that looks wrong
     * until they press Refresh.
     */
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
                // keep polling: a transient failure should not end the tour
            }
            await wait(1500)
        }
        return false
    }

    /**
     * Remove the second flow when a tour starts.
     *
     * Its Flow trigger fires on every success of the first flow, so one left behind by an earlier run
     * would be creating executions several steps before the step that introduces it.
     */
    const removeReportFlow = async () => {
        if (!(await tourFlowExists(TOUR_REPORT_FLOW_ID))) {
            return
        }
        try {
            await FlowsAPI.deleteFlow({namespace: TOUR_NAMESPACE, id: TOUR_REPORT_FLOW_ID})
        } catch {
            // Not being able to remove it is not worth stopping the tour for.
        }
        tourStore.setTourState({reportFlowCreated: false})
    }

    const createReportFlow = async () => {
        if (await tourFlowExists(TOUR_REPORT_FLOW_ID)) {
            // Left over from an earlier run: bring it back to the definition this tour describes.
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
