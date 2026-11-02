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
import type {Execution} from "../../../stores/executions"
import type {OnboardingTourEvent} from "../../../composables/useOnboardingAnalytics"
import {TOUR_DOCKER_DOCS, tourDocsUrl} from "./tourDocs"

export interface TourErrorRecovery {
    labelKey: string;
    event?: OnboardingTourEvent;
    run(context: {store: TourStore}): Promise<void> | void;
}

export interface TourErrorHelp {
    labelKey: string;
    href: string;
}

export class TourSceneError extends Error {
    constructor(
        readonly key: string,
        readonly params: Record<string, unknown> = {},
        readonly options: {
            recovery?: TourErrorRecovery;
            help?: TourErrorHelp;
            event?: OnboardingTourEvent;
        } = {},
    ) {
        super(key)
        this.name = "TourSceneError"
    }
}

export class TourDockerUnavailableError extends TourSceneError {
    constructor() {
        super("onboarding.tour.errors.docker_unavailable", {}, {
            event: "tour_docker_unavailable",
            recovery: {
                labelKey: "onboarding.tour.actions.run_without_docker",
                event: "tour_docker_fallback",
                run: ({store}) => store.setTourState({taskRunner: "process"}),
            },
            help: {
                labelKey: "onboarding.tour.actions.enable_docker",
                href: tourDocsUrl(TOUR_DOCKER_DOCS),
            },
        })
        this.name = "TourDockerUnavailableError"
    }
}

const expectState = (execution: Execution | undefined, expected: string, key: string) => {
    const state = execution?.state?.current
    if (state !== expected) {
        throw new TourSceneError(key, {state: state ?? "unknown"})
    }
}

type TourStore = ReturnType<typeof useProductTourStore>

// Generic over the actions, so a tour variant can bring its own set.
export interface TourSceneBase<A = TourActions> {
    actions: A;
    store: TourStore;
}

export interface TourSceneContext<A = TourActions> extends TourSceneBase<A> {
    signal: AbortSignal;
    onStillRunning(): void;
}

export interface TourScene<A = TourActions> {
    /** Also the i18n key suffix: `<variant i18n prefix>.scenes.<id>.*`. */
    id: string;
    step: number;
    targetSelector?: string;
    placement?: "left";
    dim?: boolean;
    milestone?: boolean;
    callout?: boolean;
    confetti?: boolean;
    offersExit?: boolean;
    enter?(context: TourSceneContext<A>): Promise<void> | void;
    action?(context: TourSceneContext<A>): Promise<void> | void;
    completedByUser?(context: TourSceneBase<A> & {route: TourRoute}): boolean;
    verify?(context: TourSceneContext<A> & {route: TourRoute}): Promise<void>;
    poll?(context: TourSceneContext<A>): Promise<boolean>;
}

type SceneShape = {id: string; step: number};

export interface TourStepGroup {
    step: number;
    scenes: string[];
}

export const sceneIdsOf = (scenes: readonly SceneShape[]) => scenes.map((scene) => scene.id)

export const stepGroupsOf = (scenes: readonly SceneShape[]): TourStepGroup[] =>
    scenes.reduce((groups, scene) => {
        const group = groups.find((candidate) => candidate.step === scene.step)
        if (group) {
            group.scenes.push(scene.id)
        } else {
            groups.push({step: scene.step, scenes: [scene.id]})
        }
        return groups
    }, [] as TourStepGroup[])

/** Falls back to the first scene, so a renamed or dropped scene id cannot strand the tour. */
export const sceneIndexOf = (scenes: readonly SceneShape[], id: string | null) => {
    const index = sceneIdsOf(scenes).indexOf(id ?? "")
    return index >= 0 ? index : 0
}

export const workingLabelKey = (sceneKey: string, genericKey: string, exists: (key: string) => boolean) =>
    exists(sceneKey) ? sceneKey : genericKey

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

const isNewTourExecution = ({store, route}: TourSceneBase & {route: TourRoute}) =>
    isTourExecution(route)
    && Boolean(route.params.id)
    && route.params.id !== store.state.tour.rejectedExecutionId

const routeExecutionId = (route: TourRoute) => route.params.id as string

const settleExecution = async (
    store: TourStore,
    executionId: string | undefined,
    check: () => Promise<void> | void,
) => {
    try {
        await check()
    } catch (e) {
        if (e instanceof TourSceneError) {
            store.setTourState({rejectedExecutionId: executionId ?? null})
        }
        throw e
    }
    store.setTourState({rejectedExecutionId: null})
}

export const checkFirstExecution = async (
    {actions, store, signal}: TourSceneContext,
    execution: Execution | undefined,
) => {
    const state = execution?.state?.current
    if (state === "SUCCESS") {
        return
    }
    if (
        state === "FAILED"
        && store.state.tour.taskRunner === "docker"
        && execution?.id
        && await actions.dockerUnavailable(execution.id, signal)
    ) {
        throw new TourDockerUnavailableError()
    }
    throw new TourSceneError("onboarding.tour.errors.first_execution", {state: state ?? "unknown"})
}

const acceptFirstExecution = (context: TourSceneContext, execution: Execution | undefined) =>
    settleExecution(context.store, execution?.id, () => checkFirstExecution(context, execution))

const acceptExpectedFailure = (context: TourSceneContext, execution: Execution | undefined) =>
    settleExecution(context.store, execution?.id, () => {
        expectState(execution, "FAILED", "onboarding.tour.errors.expected_failure")
        context.store.setTourState({failedExecutionId: execution?.id ?? null})
    })

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
        enter: async ({actions, store}) => {
            if (await actions.tourFlowExists()) {
                await actions.openEditorWith(tourFlowSource.generated(store.state.tour.taskRunner))
            }
        },
        action: async (context) => {
            const {actions, store} = context
            await actions.applySource(tourFlowSource.generated(store.state.tour.taskRunner))
            const execution = await actions.executeFlow(TOUR_FLOW_ID, context)
            await acceptFirstExecution(context, execution)
        },
        completedByUser: isNewTourExecution,
        verify: async (context) => {
            const executionId = routeExecutionId(context.route)
            const execution = await context.actions.waitForExecution(executionId, context)
            await acceptFirstExecution(context, execution)
            context.store.setTourState({lastExecutionId: executionId})
        },
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
        enter: async ({actions, store}) => {
            actions.showDocsPanel()
            await actions.openEditorWith(tourFlowSource.withBrokenNotify(store.state.tour.taskRunner))
        },
    },
    {
        id: "editor_help",
        step: 2,
        targetSelector: DOCS_PANEL,
        callout: true,
        placement: "left",
        enter: ({actions}) => actions.showTaskDocs(TOUR_NOTIFY_TASK_TYPE),
        action: async (context) => {
            const execution = await context.actions.executeFlow(TOUR_FLOW_ID, context)
            await acceptExpectedFailure(context, execution)
        },
        completedByUser: isNewTourExecution,
        verify: async (context) => {
            const executionId = routeExecutionId(context.route)
            const execution = await context.actions.waitForExecution(executionId, context)
            await acceptExpectedFailure(context, execution)
            context.store.setTourState({lastExecutionId: executionId})
        },
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
        enter: async ({actions, store}) => {
            await actions.saveSource(tourFlowSource.withFixedNotify(store.state.tour.taskRunner))
            await actions.openRevisionDiff(TOUR_FIXED_EXPRESSION)
        },
        action: async ({actions, ...context}) => {
            const execution = await actions.replayFromFailedTask(context)
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
        enter: async ({actions, store}) => {
            actions.showDocsPanel()
            await actions.openEditorWith(
                tourFlowSource.withWebhook(actions.ensureWebhookKey(), store.state.tour.taskRunner),
            )
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
        action: async ({actions, ...context}) => {
            const result = await actions.sendTestEvent(TOUR_TEST_EVENT_PAYLOAD)
            if (!result.ok) {
                throw new TourSceneError("onboarding.tour.errors.test_event", {status: result.status})
            }
            if (result.executionId) {
                await actions.waitForExecution(result.executionId, context)
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
                    {expression: "trigger.body", select: "trigger.body"},
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
        action: async ({actions, ...context}) => {
            const sentAt = new Date().toISOString()
            const result = await actions.sendTestEvent(TOUR_TEST_EVENT_PAYLOAD)
            if (result.executionId) {
                await actions.waitForExecution(result.executionId, context)
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
