// Taxonomy source of truth: kestra-io/data#354
const FLAT_EVENT_NAMES: Record<string, string> = {
    flow_created: "app.flow.created",
    secret_created: "app.secret.created",
    secret_updated: "app.secret.updated",
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
}

const OSSAUTH_NAMES: Record<string, string> = {
    forgot_password_click: "app.forgot-password.clicked",
}

const FLOW_EXECUTION_NAMES: Record<string, string> = {
    executed: "app.flow.executed",
    open_modal: "app.execute-modal.opened",
    submit: "app.execute-modal.submitted",
}

const ONBOARDING_NAMES: Record<string, string> = {
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

function resolveEditorTabAction(properties: Record<string, any>): string {
    return EDITOR_TAB_ACTION_NAMES[properties.action] ?? "editor_tab_action"
}

function resolveOssAuth(properties: Record<string, any>): string {
    return OSSAUTH_NAMES[properties.action] ?? "app.oss-auth.completed"
}

function resolveFlowExecution(properties: Record<string, any>): string {
    return FLOW_EXECUTION_NAMES[properties.action] ?? "flow_execution"
}

function resolveOnboarding(properties: Record<string, any>): string {
    return ONBOARDING_NAMES[properties.onboarding?.action] ?? "onboarding"
}

const SPLIT_EVENT_RESOLVERS: Record<string, (properties: Record<string, any>) => string> = {
    flow_execution: resolveFlowExecution,
    editor_tab_action: resolveEditorTabAction,
    ossauth: resolveOssAuth,
    onboarding: resolveOnboarding,
}

export function resolvePosthogEventName(type: string, properties: Record<string, any>): string {
    const lowerType = type.toLowerCase()

    const splitResolver = SPLIT_EVENT_RESOLVERS[lowerType]
    if (splitResolver) {
        return splitResolver(properties)
    }

    return FLAT_EVENT_NAMES[lowerType] ?? lowerType
}
