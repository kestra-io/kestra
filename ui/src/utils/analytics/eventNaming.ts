// Taxonomy source of truth: kestra-io/data#354
const FLAT_EVENT_NAMES: Record<string, string> = {
    flow_created: "app.flow.created",
    user_invited: "app.user.invited",
    ai_copilot: "app.ai-copilot.invoked",
    blueprint: "app.blueprint.used",
    survey_submitted: "app.survey.submitted",
    survey_skipped: "app.survey.skipped",
    "setup_flow:account_created": "app.account.created",
    "setup_flow:account_creation_failed": "app.account.creation-failed",
    "setup_flow:marketing_survey_submitted": "app.marketing-survey.submitted",
    "setup_flow:marketing_survey_skipped": "app.marketing-survey.skipped",
    "setup_flow:completed": "app.setup-flow.completed",
    error: "app.error.occurred",
}

const EDITOR_TAB_ACTION_NAMES: Record<string, string> = {
    open: "app.editor-tab.opened",
    close: "app.editor-tab.closed",
    plugin_doc: "app.plugin-doc.viewed",
    files_open: "app.editor-files.opened",
    blueprint_selection: "app.editor-blueprint.selected",
    task_added: "app.editor-task.added",
    task_edited: "app.editor-task.edited",
    task_deleted: "app.editor-task.deleted",
    task_moved: "app.editor-task.moved",
    task_duplicated: "app.editor-task.duplicated",
}

const OSSAUTH_NAMES: Record<string, string> = {
    forgot_password_click: "app.forgot-password.clicked",
}

const FLOW_EXECUTION_NAMES: Record<string, string> = {
    executed: "app.flow.executed",
    open_modal: "app.execute-modal.opened",
    submit: "app.execute-modal.submitted",
}

// KV entry, namespace secret and API token are three different objects, so the taxonomy gives them
// three object segments rather than one event disambiguated by a property.
const SECRET_OBJECTS: Record<string, string> = {
    kv: "kv",
    secret: "secret",
    token: "token",
}
export type EventProperties = {
    action?: string
    secret_type?: string
    onboarding?: {
        event?: string
        action?: string
    }
}
const ONBOARDING_NAMES: Record<string, string> = {
    tour_offered: "app.onboarding-tour.offered",
    tour_started: "app.onboarding-tour.started",
    tour_continued: "app.onboarding-tour.continued",
    tour_completed: "app.onboarding-tour.completed",
    tour_closed: "app.onboarding-tour.closed",
    step_viewed: "app.onboarding-step.viewed",
    step_next_clicked: "app.onboarding-step.advanced",
    step_auto_advanced: "app.onboarding-step.auto-advanced",
    step_validation_failed: "app.onboarding-step.validation-failed",
    tutorial_completed: "app.onboarding.completed",
    finish_explore_blueprints_clicked: "app.onboarding.completed",
    finish_create_flow_clicked: "app.onboarding.completed",
    tutorial_canceled: "app.onboarding.cancelled",
    flow_saved_during_tutorial: "app.onboarding-step.viewed",
    flow_executed_during_tutorial: "app.onboarding-step.viewed",
}

function resolveEditorTabAction(properties: EventProperties): string {
    const action = properties.action
    return action ? EDITOR_TAB_ACTION_NAMES[action] ?? "editor_tab_action" : "editor_tab_action"
}

function resolveOssAuth(properties: EventProperties): string {
    const action = properties.action
    return action ? OSSAUTH_NAMES[action] ?? "app.oss-auth.completed" : "app.oss-auth.completed"
}

function resolveFlowExecution(properties: EventProperties): string {
    const action = properties.action
    return action ? FLOW_EXECUTION_NAMES[action] ?? "flow_execution" : "flow_execution"
}

function resolveOnboarding(properties: EventProperties): string {
    const onboarding = properties.onboarding ?? {}
    const event = onboarding.event
    const action = onboarding.action

    return (event ? ONBOARDING_NAMES[event] : undefined)
        ?? (action ? ONBOARDING_NAMES[action] : undefined)
        ?? "onboarding"
}

function resolveSecret(action: "created" | "updated"): (properties: EventProperties) => string {
    return (properties) => {
        const secretType = properties.secret_type
        const object = secretType ? SECRET_OBJECTS[secretType] : undefined
        return object ? `app.${object}.${action}` : `secret_${action}`
    }
}

const SPLIT_EVENT_RESOLVERS: Record<string, (properties: EventProperties) => string> = {
    flow_execution: resolveFlowExecution,
    secret_created: resolveSecret("created"),
    secret_updated: resolveSecret("updated"),
    editor_tab_action: resolveEditorTabAction,
    ossauth: resolveOssAuth,
    onboarding: resolveOnboarding,
}

export function resolvePosthogEventName(type: string, properties: EventProperties): string {
    const lowerType = type.toLowerCase()

    const splitResolver = SPLIT_EVENT_RESOLVERS[lowerType]
    if (splitResolver) {
        return splitResolver(properties)
    }

    return FLAT_EVENT_NAMES[lowerType] ?? lowerType
}
