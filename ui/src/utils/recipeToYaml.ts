import {flowYamlUtils} from "@kestra-io/topology"

export type TriggerType = "execution" | "schedule" | "webhook" | "other"

export const SYSTEM_FLOW_RECIPE_ID = "system-flow-alert"

export const DEFAULT_WEBHOOK_KEY = "my-webhook-key"

export const DEFAULT_CRON = "0 9 * * *"

export const DEFAULT_STATES = ["FAILED", "WARNING"]

export const DEFAULT_SLACK_CHANNEL = "#alerts"

export const DEFAULT_EMAIL_TO = "team@your-domain.com"

export interface RecipeState {
    triggerType: TriggerType
    watchNamespace: string
    includeSub: boolean
    states: string[]
    cron: string
    timezone: string
    webhookKey: string
    otherTriggerType: string
    notify: {
        slack: boolean
        teams: boolean
        email: boolean
        custom: boolean
    }
    slackChannel: string
    teamsWebhook: string
    emailTo: string
}

export type NotifyChannel = "slack" | "teams" | "email" | "custom"

interface NotifyTaskConfig {
    executionFqcn: string
    webhookFqcn: string
}

export const NOTIFY_TASK_CONFIGS: Record<Exclude<NotifyChannel, "custom">, NotifyTaskConfig> = {
    slack: {
        executionFqcn: "io.kestra.plugin.slack.notifications.SlackExecution",
        webhookFqcn: "io.kestra.plugin.slack.notifications.SlackIncomingWebhook",
    },
    teams: {
        executionFqcn: "io.kestra.plugin.microsoft365.teams.TeamsExecution",
        webhookFqcn: "io.kestra.plugin.microsoft365.teams.TeamsIncomingWebhook",
    },
    email: {
        executionFqcn: "io.kestra.plugin.email.MailExecution",
        webhookFqcn: "io.kestra.plugin.email.MailSend",
    },
}

function buildExecutionTrigger(state: RecipeState): object[] {
    const conditions: object[] = []
    if (state.watchNamespace) {
        conditions.push({
            type: "io.kestra.plugin.core.condition.ExecutionNamespace",
            namespace: state.watchNamespace,
            prefix: state.includeSub,
        })
    }

    const trigger: Record<string, unknown> = {
        id: "on_flow_state",
        type: "io.kestra.plugin.core.trigger.Flow",
        states: state.states.length > 0 ? state.states : DEFAULT_STATES,
    }

    if (conditions.length > 0) {
        trigger.conditions = conditions
    }

    return [trigger]
}

function buildScheduleTrigger(state: RecipeState): object[] {
    const trigger: Record<string, unknown> = {
        id: "on_schedule",
        type: "io.kestra.plugin.core.trigger.Schedule",
        cron: state.cron || DEFAULT_CRON,
    }
    if (state.timezone) {
        trigger.timezone = state.timezone
    }
    return [trigger]
}

function buildWebhookTrigger(state: RecipeState): object[] {
    return [
        {
            id: "on_webhook",
            type: "io.kestra.plugin.core.trigger.Webhook",
            key: state.webhookKey || DEFAULT_WEBHOOK_KEY,
        },
    ]
}

function buildOtherTrigger(state: RecipeState): object[] {
    if (!state.otherTriggerType) return []
    return [
        {
            id: "on_trigger",
            type: state.otherTriggerType,
        },
    ]
}

const TEAMS_CARD_PAYLOAD = `{
  "@type": "MessageCard",
  "@context": "http://schema.org/extensions",
  "themeColor": "8405FF",
  "summary": "Kestra notification",
  "sections": [
    {
      "activityTitle": "{{ flow.namespace }}.{{ flow.id }} was triggered",
      "markdown": true
    }
  ]
}
`

export function notifyTaskFqcn(channel: Exclude<NotifyChannel, "custom">, isExecutionTrigger: boolean): string {
    const config = NOTIFY_TASK_CONFIGS[channel]
    return isExecutionTrigger ? config.executionFqcn : config.webhookFqcn
}

function buildSlackTask(state: RecipeState, isExecutionTrigger: boolean, fqcn: string): Record<string, unknown> {
    const task: Record<string, unknown> = {
        id: "notify_slack",
        type: fqcn,
        url: "{{ secret('SLACK_WEBHOOK') }}",
    }

    if (isExecutionTrigger) {
        task.channel = state.slackChannel || DEFAULT_SLACK_CHANNEL
        task.executionId = "{{ trigger.executionId }}"
    } else {
        task.messageText = "{{ flow.namespace }}.{{ flow.id }} was triggered."
    }

    return task
}

function buildTeamsTask(state: RecipeState, isExecutionTrigger: boolean, fqcn: string): Record<string, unknown> {
    const task: Record<string, unknown> = {
        id: "notify_teams",
        type: fqcn,
        url: state.teamsWebhook || "{{ secret('TEAMS_WEBHOOK') }}",
    }

    if (isExecutionTrigger) {
        task.executionId = "{{ trigger.executionId }}"
    } else {
        task.payload = TEAMS_CARD_PAYLOAD
    }

    return task
}

function buildEmailTask(state: RecipeState, isExecutionTrigger: boolean, fqcn: string): Record<string, unknown> {
    const task: Record<string, unknown> = {
        id: "notify_email",
        type: fqcn,
        host: "{{ secret('EMAIL_HOST') }}",
        port: 465,
        username: "{{ secret('EMAIL_USERNAME') }}",
        password: "{{ secret('EMAIL_PASSWORD') }}",
        from: "kestra@your-domain.com",
        to: state.emailTo || DEFAULT_EMAIL_TO,
        subject: "{{ flow.namespace }}.{{ flow.id }} notification",
    }

    if (isExecutionTrigger) {
        task.executionId = "{{ trigger.executionId }}"
    } else {
        task.htmlTextContent = "{{ flow.namespace }}.{{ flow.id }} was triggered."
    }

    return task
}

const NOTIFY_TASK_BUILDERS: Record<
    Exclude<NotifyChannel, "custom">,
    (state: RecipeState, isExecutionTrigger: boolean, fqcn: string) => Record<string, unknown>
> = {
    slack: buildSlackTask,
    teams: buildTeamsTask,
    email: buildEmailTask,
}

function buildNotifyTasks(state: RecipeState, isExecutionTrigger: boolean, availableFqcns: Set<string>): object[] {
    const tasks: object[] = []

    for (const channel of ["slack", "teams", "email"] as const) {
        if (!state.notify[channel]) continue

        const fqcn = notifyTaskFqcn(channel, isExecutionTrigger)
        if (availableFqcns.size > 0 && !availableFqcns.has(fqcn)) continue

        tasks.push(NOTIFY_TASK_BUILDERS[channel](state, isExecutionTrigger, fqcn))
    }

    if (state.notify.custom) {
        tasks.push({
            id: "notify_custom",
            type: "io.kestra.plugin.core.log.Log",
            message: isExecutionTrigger
                ? "Replace this task with your notification of choice: execution {{ trigger.executionId }} reached state {{ trigger.state }}."
                : "Replace this task with your notification of choice.",
        })
    }

    return tasks
}

export function recipeToFlowObject(
    state: RecipeState,
    systemNamespace: string,
    availableFqcns: Set<string> = new Set(),
    flowId: string = SYSTEM_FLOW_RECIPE_ID,
): Record<string, unknown> {
    const isExecutionTrigger = state.triggerType === "execution"
    const tasks = buildNotifyTasks(state, isExecutionTrigger, availableFqcns)

    let triggers: object[]
    switch (state.triggerType) {
    case "execution":
        triggers = buildExecutionTrigger(state)
        break
    case "schedule":
        triggers = buildScheduleTrigger(state)
        break
    case "webhook":
        triggers = buildWebhookTrigger(state)
        break
    case "other":
        triggers = buildOtherTrigger(state)
        break
    default:
        triggers = []
    }

    const flowObj: Record<string, unknown> = {
        id: flowId,
        namespace: systemNamespace,
        tasks: tasks.length > 0 ? tasks : [{id: "placeholder", type: "io.kestra.plugin.core.log.Log", message: "Configure your notification tasks"}],
        triggers,
    }

    return flowObj
}

export function recipeToYaml(state: RecipeState, systemNamespace: string, availableFqcns: Set<string> = new Set(), flowId: string = SYSTEM_FLOW_RECIPE_ID): string {
    const flowObj = recipeToFlowObject(state, systemNamespace, availableFqcns, flowId)
    return flowYamlUtils.stringify(flowObj)
}
