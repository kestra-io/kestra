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

/**
 * How many groups the numbered steps are shown in.
 *
 * The card counts every step ("step 5 of 13"), and the progress bar puts them in these groups, so
 * the number, the filled ticks and the number of things left to do all say the same thing.
 */
export const TOUR_STEP_GROUP_COUNT = 4

/** An error the card shows translated, rather than a raw message. */
export class TourSceneError extends Error {
    constructor(
        readonly key: string,
        readonly params: Record<string, unknown> = {},
    ) {
        super(key)
        this.name = "TourSceneError"
    }
}

/**
 * The next card describes what just happened, so a scene stops rather than narrate something the
 * execution did not do. The message ends up in the card, and the button can be pressed again.
 */
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
    /** Numbered step shown in the card, several scenes can share one step. */
    step: number;
    /** Element highlighted in the real UI while the scene is on screen. */
    targetSelector?: string;
    /** Which corner the card sits in, for scenes whose content is under the default one. */
    placement?: "left";
    /** Shows the green milestone badge once the scene's work has finished. */
    milestone?: boolean;
    /** Renders the `callout` translation under the body. */
    callout?: boolean;
    /** Celebrates with confetti when the scene settles. */
    confetti?: boolean;
    /** Offers an early exit to the finale, like "Finish tour" on the last optional step. */
    offersExit?: boolean;
    /** Brings the app to the state the scene describes. Runs on entry, also when going back. */
    enter?: (context: TourSceneContext) => Promise<void> | void;
    /** What the primary button does. The card shows a spinner until it resolves. */
    action?: (context: TourSceneContext) => Promise<void> | void;
    /**
     * Whether the user has already done this step in the real UI, in which case the tour moves on by
     * itself rather than asking for its own button as well.
     *
     * Checked whenever the route or the tour's own state changes, so it has to be false in the state
     * the scene's own `enter` leaves behind. It may take over what the user created, which is how the
     * next scene knows which execution to open.
     */
    completedByUser?: (context: TourSceneContext & {route: TourRoute}) => boolean;
    /**
     * Checked every couple of seconds while the scene is on screen, for work that leaves no trace in
     * the app until it is looked for: an HTTP request sent from outside the browser, for instance.
     *
     * Returning true means the state it was waiting for has been recorded, which `completedByUser`
     * then sees.
     */
    poll?: (context: TourSceneContext) => Promise<boolean>;
}

/** What a scene needs from the current route: which page it is, and what it shows. */
export interface TourRoute {
    name?: string | symbol | null;
    params: Record<string, unknown>;
}

/**
 * Whether the route shows an execution of the tour's own flow.
 *
 * The tabs of both pages are child routes (`executions/update/gantt`, `flows/update/edit`, ...), so
 * the page is the prefix of the route name and any of its tabs counts.
 */
const isTourExecution = (route: TourRoute) =>
    String(route.name ?? "").startsWith(EXECUTION_PARENT_ROUTE)
    && route.params.namespace === TOUR_NAMESPACE
    && route.params.flowId === TOUR_FLOW_ID

const isFlowEditor = (route: TourRoute) => String(route.name ?? "").startsWith(FLOW_PARENT_ROUTE)

/** Take over an execution the user started, so the scenes that follow talk about theirs. */
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

const EXECUTE_BUTTON = "[data-onboarding-target=\"flow-execute-button\"], #execute-button"
const EDITOR = "#flowFileEditorTab"
const DOCS_PANEL = ".plugin-doc-wrapper, .plugin-list-wrapper"
const GANTT = "[data-onboarding-target=\"execution-gantt\"], #gantt"
// Both revision selectors, rather than the grid around them: its columns line up with the diff
// panes below, so a glow around the grid would take in half-empty columns.
const REVISION_DIFF = ".revision-select"
const TEST_EVENT_BUTTON = "[data-onboarding-target=\"trigger-test-event-button\"]"
// The button the card asks for, rather than the panel around it: the glow then has the button's own
// padding instead of hugging the panel's first and last line.
const EXPRESSION_DEBUGGER = ".expression-debugger .button"

export const TOUR_SCENES: TourScene[] = [
    /* Step 1 - Copilot writes the first flow */
    {
        id: "copilot",
        step: 1,
        enter: async ({actions}) => {
            await actions.removeReportFlow()
            await actions.openCopilot()
        },
        // The example flow rather than a model call: the tour has to describe exactly the flow that
        // comes back, and it has to work on an instance with no AI provider configured.
        action: ({actions}) => actions.generateFlow(),
        // Leaving the Copilot page means the flow was created from the chat instead.
        completedByUser: ({route}) => route.name !== "ai",
    },
    {
        id: "flow_generated",
        step: 1,
        targetSelector: EXECUTE_BUTTON,
        enter: async ({actions}) => {
            // A flow left behind by an earlier run still has the notify task, the log and the
            // trigger in it. The card describes two tasks, so the two tasks are what gets saved and
            // shown, as a new revision of the flow.
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

    /* Step 2 - edit by hand, watch it fail, fix it, replay */
    {
        id: "add_task",
        step: 2,
        targetSelector: EDITOR,
        callout: true,
        // The task is in the editor, and saved, before the card describes it. The Docs panel is put
        // in the layout first, so the editor opens with it on the scene that follows.
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
        // The docs are in the right-hand panel, so the card takes the editor's side of the screen.
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
        targetSelector: GANTT,
        callout: true,
        enter: async ({actions, store}) => {
            const executionId = store.state.tour.failedExecutionId
            if (executionId) {
                // Expands the failed task run, so its error is on screen next to the card.
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
        // The changed line is the last one of the flow, so the card moves out of the new revision's
        // pane instead of sitting on top of it.
        placement: "left",
        // Saved first, then shown: the diff on screen is this one-line fix, opened on the line that
        // changed.
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
        targetSelector: GANTT,
        milestone: true,
        callout: true,
        enter: async ({actions, store}) => {
            const executionId = store.state.tour.restartedExecutionId
            if (executionId) {
                // Expands the task that ran again, so its logs sit next to the card.
                await actions.openExecution(executionId, undefined, {autoExpandGantt: "notify"})
            }
        },
        completedByUser: ({route}) => isFlowEditor(route),
    },

    /* Step 3 - the flow starts itself */
    {
        id: "webhook_trigger",
        step: 3,
        targetSelector: EDITOR,
        // The trigger is in the editor, and saved, while the card explains it. The Docs panel follows
        // what is being edited, so it is pointed at the trigger that was just added.
        enter: async ({actions}) => {
            actions.showDocsPanel()
            await actions.openEditorWith(tourFlowSource.withWebhook(actions.ensureWebhookKey()))
            await actions.showTaskDocs(TOUR_WEBHOOK_TRIGGER_TYPE)
        },
        action: ({actions}) => actions.openTriggersTab(),
        completedByUser: ({route}) => route.name === `${FLOW_PARENT_ROUTE}/triggers`,
    },
    {
        id: "test_event",
        step: 3,
        targetSelector: TEST_EVENT_BUTTON,
        // Forgotten on entry, so a second visit is not taken for an event that has just been sent.
        enter: async ({actions, store}) => {
            store.setTourState({eventExecutionId: null, eventWatchSince: new Date().toISOString()})
            await actions.openTriggersTab()
        },
        // The URL can be copied from the editor and called with curl or from another tab. Whatever
        // sent the request, the execution it created is the one the next step talks about.
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
        // One click sends the event. The dialog with an editable payload stays available from the
        // trigger row for anyone who wants to change it, and sending from there counts just as much.
        action: async ({actions, store}) => {
            const result = await actions.sendTestEvent(TOUR_TEST_EVENT_PAYLOAD)
            if (!result.ok) {
                throw new TourSceneError("onboarding.tour.errors.test_event", {status: result.status})
            }
            if (result.executionId) {
                await actions.waitForExecution(result.executionId)
            }
            store.recordExecution()
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
        callout: true,
        offersExit: true,
        // The debugger is the right-hand panel, and the card tells the user to press its button.
        placement: "left",
        // The same execution, on the tab where its context can be read and expressions evaluated
        // against it, with the trigger payload already in the debugger.
        enter: async ({actions, store}) => {
            const executionId = store.state.tour.eventExecutionId
            if (executionId) {
                await actions.openExecution(
                    executionId,
                    undefined,
                    // The payload is opened in the viewer, and the debugger is seeded with the
                    // expression that reads it.
                    {expression: "trigger.body", select: "trigger.variables"},
                    "outputs",
                )
            }
        },
        action: ({actions}) => actions.createReportFlow(),
    },

    /* Step 4 - flows react to flows */
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
            // Only then open the list, so it already contains the execution the Flow trigger
            // started instead of needing a manual refresh.
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

/** Every scene is one numbered step of the tour. */
export const TOUR_TOTAL_STEPS = TOUR_SCENES.length

/**
 * Scenes grouped by the step they belong to, so the card can show five steps with their substeps
 * instead of a dozen numbered steps.
 */
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
