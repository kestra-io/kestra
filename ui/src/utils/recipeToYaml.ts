import {flowYamlUtils} from "@kestra-io/topology"

export type TriggerType = "execution" | "schedule" | "webhook" | "other"

export const SYSTEM_FLOW_RECIPE_ID = "system-flow-alert"

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

interface NotifyTaskConfig {
    executionFqcn: string
    webhookFqcn: string
}

export const NOTIFY_TASK_CONFIGS: Record<string, NotifyTaskConfig> = {
    slack: {
        executionFqcn: "io.kestra.plugin.slack.notifications.SlackExecution",
        webhookFqcn: "io.kestra.plugin.slack.notifications.SlackIncomingWebhook",
    },
    teams: {
        executionFqcn: "io.kestra.plugin.microsoft365.teams.TeamsExecution",
        webhookFqcn: "io.kestra.plugin.microsoft365.teams.TeamsIncomingWebhook",
    },
    email: {
        executionFqcn: "io.kestra.plugin.email.MailSend",
        webhookFqcn: "io.kestra.plugin.email.MailSend",
    },
}

const FALLBACK_STATES = ["FAILED", "WARNING"]

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
        states: state.states.length > 0 ? state.states : FALLBACK_STATES,
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
        cron: state.cron || "0 9 * * *",
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
            key: state.webhookKey || "my-webhook-key",
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

function buildNotifyTasks(state: RecipeState, isExecutionTrigger: boolean, availableFqcns: Set<string>): object[] {
    const tasks: object[] = []

    if (state.notify.slack) {
        const fqcn = isExecutionTrigger
            ? NOTIFY_TASK_CONFIGS.slack.executionFqcn
            : NOTIFY_TASK_CONFIGS.slack.webhookFqcn

        if (availableFqcns.size === 0 || availableFqcns.has(fqcn)) {
            const task: Record<string, unknown> = {
                id: "notify_slack",
                type: fqcn,
                url: "{{ secret('SLACK_WEBHOOK') }}",
                channel: state.slackChannel || "#alerts",
            }
            if (isExecutionTrigger) {
                task.executionId = "{{ trigger.executionId }}"
            } else {
                task.payload = "{\"text\": \"Flow {{ flow.id }} triggered\"}"
            }
            tasks.push(task)
        }
    }

    if (state.notify.teams) {
        const fqcn = isExecutionTrigger
            ? NOTIFY_TASK_CONFIGS.teams.executionFqcn
            : NOTIFY_TASK_CONFIGS.teams.webhookFqcn

        if (availableFqcns.size === 0 || availableFqcns.has(fqcn)) {
            const task: Record<string, unknown> = {
                id: "notify_teams",
                type: fqcn,
                url: state.teamsWebhook || "{{ secret('TEAMS_WEBHOOK') }}",
            }
            if (isExecutionTrigger) {
                task.executionId = "{{ trigger.executionId }}"
            } else {
                task.message = "Flow {{ flow.id }} triggered"
            }
            tasks.push(task)
        }
    }

    if (state.notify.email) {
        const fqcn = isExecutionTrigger
            ? NOTIFY_TASK_CONFIGS.email.executionFqcn
            : NOTIFY_TASK_CONFIGS.email.webhookFqcn

        if (availableFqcns.size === 0 || availableFqcns.has(fqcn)) {
            const task: Record<string, unknown> = {
                id: "notify_email",
                type: fqcn,
                from: "kestra@your-domain.com",
                to: [state.emailTo || "team@your-domain.com"],
                subject: "Flow {{ flow.id }} notification",
                htmlTextContent: isExecutionTrigger
                    ? "Execution {{ trigger.executionId }} completed with state {{ trigger.state }}."
                    : "Flow {{ flow.id }} was triggered.",
            }
            tasks.push(task)
        }
    }

    if (state.notify.custom) {
        tasks.push({
            id: "notify_custom",
            type: "io.kestra.plugin.core.log.Log",
            message: isExecutionTrigger
                ? "Replace this task with your notification of choice — execution {{ trigger.executionId }} reached state {{ trigger.state }}."
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
