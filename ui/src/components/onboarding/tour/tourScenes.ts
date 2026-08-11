import type {TourActions} from "./useTourActions"
import {FLOW_PARENT_ROUTE} from "../../flows/flowTabs"
import {EXECUTION_PARENT_ROUTE} from "../../executions/executionTabs"
import type {useProductTourStore} from "../../../stores/productTour"
import {
    TOUR_FIXED_EXPRESSION,
    TOUR_FLOW_ID,
    TOUR_NAMESPACE,
    TOUR_NOTIFY_TASK_TYPE,
    TOUR_TEST_EVENT_PAYLOAD,
    TOUR_WEBHOOK_TRIGGER_TYPE,
    tourFlowSource,
} from "./tourFlows"

export const TOUR_STEP_GROUP_COUNT = 4

export class TourSceneError extends Error {
    constructor(
        readonly key: string,
        readonly params: Record<string, unknown> = {},
    ) {
        super(key)
        this.name = "TourSceneError"
    }
}

const expectState = (execution: any, expected: string, key: string) => {
    const state = execution?.state?.current
    if (state !== expected) {
        throw new TourSceneError(key, {state: state ?? "unknown"})
    }
}

export interface TourSceneContext {
    actions: TourActions;
    store: ReturnType<typeof useProductTourStore>;
}

export interface TourScene {
    /** Also the i18n key suffix: `onboarding.tour.scenes.<id>.*`. */
    id: string;
    step: number;
    targetSelector?: string;
    placement?: "left";
    dim?: boolean;
    milestone?: boolean;
    callout?: boolean;
    confetti?: boolean;
    offersExit?: boolean;
    enter?: (context: TourSceneContext) => Promise<void> | void;
    action?: (context: TourSceneContext) => Promise<void> | void;
    completedByUser?: (context: TourSceneContext & {route: TourRoute}) => boolean;
    poll?: (context: TourSceneContext) => Promise<boolean>;
}

export interface TourRoute {
    name?: string | symbol | null;
    params: Record<string, unknown>;
}

const isTourExecution = (route: TourRoute) =>
    String(route.name ?? "").startsWith(EXECUTION_PARENT_ROUTE)
    && route.params.namespace === TOUR_NAMESPACE
    && route.params.flowId === TOUR_FLOW_ID

const isTourFlow = (route: TourRoute) =>
    route.params.namespace === TOUR_NAMESPACE && route.params.id === TOUR_FLOW_ID

const isFlowEditor = (route: TourRoute) =>
    String(route.name ?? "").startsWith(FLOW_PARENT_ROUTE) && isTourFlow(route)

const adoptExecution = (
    {store, route}: TourSceneContext & {route: TourRoute},
    extra: Record<string, string> = {},
) => {
    const executionId = route.params.id as string | undefined
    if (!executionId) {
        return false
    }
    store.setTourState({lastExecutionId: executionId, ...extra})
    return true
}

// Ranked: the first selector is what the card is about, the rest keep something lit until it renders.
const EDITOR = "#flowFileEditorTab"
const DOCS_PANEL = ".plugin-doc-wrapper, .plugin-list-wrapper"
const GANTT = "[data-onboarding-target=\"execution-gantt\"], #gantt"
const FAILED_LOG = `.log-row-error, .task-details, ${GANTT}`
const REPLAYED_TASK = `.task-details, ${GANTT}`
const REVISION_DIFF = ".revision .ks-editor, .revision-select"
const TEST_EVENT_BUTTON = "[data-onboarding-target=\"trigger-test-event-button\"]"
const EXPRESSION_DEBUGGER = ".expression-debugger .button"

export const TOUR_SCENES: TourScene[] = [
    {
        id: "copilot",
        step: 1,
        enter: async ({actions}) => {
            await actions.removeReportFlow()
            await actions.openCopilot()
        },
        action: ({actions}) => actions.generateFlow(),
        completedByUser: ({route}) => route.name === "flows/create" || isFlowEditor(route),
    },
    {
        id: "flow_generated",
        step: 1,
        targetSelector: EDITOR,
        placement: "left",
        enter: async ({actions}) => {
            if (await actions.tourFlowExists()) {
                await actions.openEditorWith(tourFlowSource.generated())
            }
        },
        action: async ({actions}) => {
            await actions.applySource(tourFlowSource.generated())
            const execution = await actions.executeFlow()
            expectState(execution, "SUCCESS", "onboarding.tour.errors.first_execution")
        },
        completedByUser: (context) =>
            isTourExecution(context.route) && adoptExecution(context),
    },

    {
        id: "first_execution",
        step: 1,
        targetSelector: GANTT,
        milestone: true,
        confetti: true,
        callout: true,
        enter: async ({actions, store}) => {
            const executionId = store.state.tour.lastExecutionId
            if (executionId) {
                await actions.openExecution(executionId)
            }
        },
        completedByUser: ({route}) => isFlowEditor(route),
    },

    {
        id: "add_task",
        step: 2,
        targetSelector: EDITOR,
        callout: true,
        enter: async ({actions}) => {
            actions.showDocsPanel()
            await actions.openEditorWith(tourFlowSource.withBrokenNotify())
        },
    },
    {
        id: "editor_help",
        step: 2,
        targetSelector: DOCS_PANEL,
        callout: true,
        placement: "left",
        enter: ({actions}) => actions.showTaskDocs(TOUR_NOTIFY_TASK_TYPE),
        action: async ({actions, store}) => {
            const execution = await actions.executeFlow()
            expectState(execution, "FAILED", "onboarding.tour.errors.expected_failure")
            store.setTourState({failedExecutionId: store.state.tour.lastExecutionId})
        },
        completedByUser: (context) =>
            isTourExecution(context.route)
            && adoptExecution(context, {failedExecutionId: context.route.params.id as string}),
    },
    {
        id: "failed_execution",
        step: 2,
        targetSelector: FAILED_LOG,
        callout: true,
        enter: async ({actions, store}) => {
            const executionId = store.state.tour.failedExecutionId
            if (executionId) {
                await actions.openExecution(executionId, undefined, {
                    autoExpandGantt: "failed",
                    "filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR",
                })
            }
        },
        action: ({actions}) => actions.openFlowEditor(),
        completedByUser: ({route}) => isFlowEditor(route),
    },
    {
        id: "fix_and_replay",
        step: 2,
        targetSelector: REVISION_DIFF,
        placement: "left",
        enter: async ({actions}) => {
            await actions.saveSource(tourFlowSource.withFixedNotify())
            await actions.openRevisionDiff(TOUR_FIXED_EXPRESSION)
        },
        action: async ({actions}) => {
            const execution = await actions.replayFromFailedTask()
            expectState(execution, "SUCCESS", "onboarding.tour.errors.replay")
        },
    },
    {
        id: "replayed_execution",
        step: 2,
        targetSelector: REPLAYED_TASK,
        milestone: true,
        callout: true,
        enter: async ({actions, store}) => {
            const executionId = store.state.tour.restartedExecutionId
            if (executionId) {
                await actions.openExecution(executionId, undefined, {autoExpandGantt: "notify"})
            }
        },
        completedByUser: ({route}) => isFlowEditor(route),
    },

    {
        id: "webhook_trigger",
        step: 3,
        targetSelector: EDITOR,
        enter: async ({actions}) => {
            actions.showDocsPanel()
            await actions.openEditorWith(tourFlowSource.withWebhook(actions.ensureWebhookKey()))
            await actions.showTaskDocs(TOUR_WEBHOOK_TRIGGER_TYPE)
        },
        action: ({actions}) => actions.openTriggersTab(),
        completedByUser: ({route}) =>
            route.name === `${FLOW_PARENT_ROUTE}/triggers` && isTourFlow(route),
    },
    {
        id: "test_event",
        step: 3,
        targetSelector: TEST_EVENT_BUTTON,
        enter: async ({actions, store}) => {
            store.setTourState({eventExecutionId: null, eventWatchSince: new Date().toISOString()})
            await actions.openTriggersTab()
        },
        poll: async ({actions, store}) => {
            const since = store.state.tour.eventWatchSince
            if (!since) {
                return false
            }
            const executionId = await actions.findEventExecution(since)
            if (!executionId) {
                return false
            }
            store.setTourState({eventExecutionId: executionId, lastExecutionId: executionId})
            return true
        },
        action: async ({actions}) => {
            const result = await actions.sendTestEvent(TOUR_TEST_EVENT_PAYLOAD)
            if (!result.ok) {
                throw new TourSceneError("onboarding.tour.errors.test_event", {status: result.status})
            }
            if (result.executionId) {
                await actions.waitForExecution(result.executionId)
            }
        },
        completedByUser: ({store}) => Boolean(store.state.tour.eventExecutionId),
    },
    {
        id: "event_execution",
        step: 3,
        milestone: true,
        confetti: true,
        offersExit: true,
        enter: async ({actions, store}) => {
            const executionId = store.state.tour.eventExecutionId
            if (executionId) {
                await actions.openExecution(executionId)
                return
            }
            await actions.openExecutionsList()
        },
    },
    {
        id: "explore_payload",
        step: 3,
        targetSelector: EXPRESSION_DEBUGGER,
        dim: false,
        callout: true,
        offersExit: true,
        enter: async ({actions, store}) => {
            const executionId = store.state.tour.eventExecutionId
            if (executionId) {
                await actions.openExecution(
                    executionId,
                    undefined,
                    {expression: "trigger.body", select: "trigger.variables"},
                    "outputs",
                )
            }
        },
        action: ({actions}) => actions.createReportFlow(),
    },

    {
        id: "report_flow",
        step: 4,
        targetSelector: EDITOR,
        callout: true,
        enter: ({actions}) => actions.createReportFlow(),
        action: async ({actions}) => {
            const sentAt = new Date().toISOString()
            const result = await actions.sendTestEvent(TOUR_TEST_EVENT_PAYLOAD)
            if (result.executionId) {
                await actions.waitForExecution(result.executionId)
            }
            await actions.waitForReportExecution(sentAt)
            await actions.openExecutionsList()
        },
    },
    {
        id: "chain",
        step: 4,
        milestone: true,
        confetti: true,
        enter: ({actions}) => actions.openExecutionsList(),
    },
]

export const TOUR_SCENE_IDS = TOUR_SCENES.map((scene) => scene.id)

export const TOUR_TOTAL_STEPS = TOUR_SCENES.length

export const TOUR_STEP_GROUPS: {step: number; scenes: string[]}[] = TOUR_SCENES.reduce(
    (groups, scene) => {
        const group = groups.find((candidate) => candidate.step === scene.step)
        if (group) {
            group.scenes.push(scene.id)
        } else {
            groups.push({step: scene.step, scenes: [scene.id]})
        }
        return groups
    },
    [] as {step: number; scenes: string[]}[],
)

export const tourSceneIndex = (id: string | null) => {
    const index = TOUR_SCENE_IDS.indexOf(id ?? "")
    return index >= 0 ? index : 0
}
