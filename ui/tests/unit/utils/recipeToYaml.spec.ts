import {describe, expect, it} from "vitest"
import {flowYamlUtils} from "@kestra-io/topology"
import {recipeToFlowObject, recipeToYaml, SYSTEM_FLOW_RECIPE_ID, type RecipeState} from "../../../src/utils/recipeToYaml"

const baseState = (): RecipeState => ({
    triggerType: "execution",
    watchNamespace: "company.team",
    includeSub: true,
    states: ["FAILED", "WARNING"],
    cron: "0 9 * * *",
    timezone: "Europe/Paris",
    webhookKey: "my-key",
    otherTriggerType: "",
    notify: {slack: false, teams: false, email: false, custom: false},
    slackChannel: "#alerts",
    teamsWebhook: "",
    emailTo: "",
})

describe("recipeToYaml", () => {
    describe("execution trigger", () => {
        it("generates valid YAML with namespace prefix=true for includeSub", () => {
            // Given
            const state = baseState()
            state.notify.slack = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            expect(parsed.namespace).toBe("system")
            const trigger = parsed.triggers[0]
            expect(trigger.type).toBe("io.kestra.plugin.core.trigger.Flow")
            expect(trigger.states).toEqual(["FAILED", "WARNING"])
            expect(trigger.conditions[0].type).toBe("io.kestra.plugin.core.condition.ExecutionNamespace")
            expect(trigger.conditions[0].namespace).toBe("company.team")
            expect(trigger.conditions[0].prefix).toBe(true)
        })

        it("generates prefix=false when includeSub is false", () => {
            // Given
            const state = baseState()
            state.includeSub = false
            state.notify.slack = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            expect(parsed.triggers[0].conditions[0].prefix).toBe(false)
        })

        it("falls back to FAILED and WARNING when states array is empty", () => {
            // Given
            const state = baseState()
            state.states = []
            state.notify.email = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            expect(parsed.triggers[0].states).toEqual(["FAILED", "WARNING"])
        })

        it("omits conditions when watchNamespace is empty", () => {
            // Given
            const state = baseState()
            state.watchNamespace = ""
            state.notify.slack = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            expect(parsed.triggers[0].conditions).toBeUndefined()
        })

        it("uses SlackExecution task (not IncomingWebhook) for execution trigger", () => {
            // Given
            const state = baseState()
            state.notify.slack = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            const slackTask = parsed.tasks.find((t: any) => t.id === "notify_slack")
            expect(slackTask.type).toBe("io.kestra.plugin.slack.notifications.SlackExecution")
            expect(slackTask.executionId).toBe("{{ trigger.executionId }}")
        })

        it("includes teams and email tasks when enabled", () => {
            // Given
            const state = baseState()
            state.notify.teams = true
            state.notify.email = true
            state.emailTo = "ops@example.com"

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            const taskTypes = parsed.tasks.map((t: any) => t.type)
            expect(taskTypes).toContain("io.kestra.plugin.microsoft365.teams.TeamsExecution")
            expect(taskTypes).toContain("io.kestra.plugin.email.MailExecution")
        })

        it("email uses executionFqcn for execution trigger", () => {
            // Given
            const state = baseState()
            state.notify.email = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            const emailTask = parsed.tasks.find((t: any) => t.id === "notify_email")
            expect(emailTask.type).toBe("io.kestra.plugin.email.MailExecution")
            expect(emailTask.executionId).toContain("trigger.executionId")
        })
    })

    describe("schedule trigger", () => {
        it("uses SlackIncomingWebhook for schedule trigger", () => {
            // Given
            const state = baseState()
            state.triggerType = "schedule"
            state.notify.slack = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            const slackTask = parsed.tasks.find((t: any) => t.id === "notify_slack")
            expect(slackTask.type).toBe("io.kestra.plugin.slack.notifications.SlackIncomingWebhook")
            expect(slackTask.executionId).toBeUndefined()
        })

        it("slack IncomingWebhook sends messageText, not a channel it cannot honour", () => {
            // Given
            const state = baseState()
            state.triggerType = "schedule"
            state.notify.slack = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            const slackTask = parsed.tasks.find((t: any) => t.id === "notify_slack")
            expect(slackTask.messageText).toContain("flow.id")
            expect(slackTask.channel).toBeUndefined()
        })

        it("generates schedule trigger with cron and timezone", () => {
            // Given
            const state = baseState()
            state.triggerType = "schedule"
            state.cron = "0 8 * * 1"
            state.timezone = "America/New_York"
            state.notify.email = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            const trigger = parsed.triggers[0]
            expect(trigger.type).toBe("io.kestra.plugin.core.trigger.Schedule")
            expect(trigger.cron).toBe("0 8 * * 1")
            expect(trigger.timezone).toBe("America/New_York")
        })

        it("email uses webhookFqcn for non-execution trigger", () => {
            // Given
            const state = baseState()
            state.triggerType = "schedule"
            state.notify.email = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            const emailTask = parsed.tasks.find((t: any) => t.id === "notify_email")
            expect(emailTask.type).toBe("io.kestra.plugin.email.MailSend")
            expect(emailTask.executionId).toBeUndefined()
            expect(emailTask.htmlTextContent).not.toContain("trigger.executionId")
        })

        it("custom channel adds an editable Log placeholder task", () => {
            // Given
            const state = baseState()
            state.notify.custom = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            const customTask = parsed.tasks.find((t: any) => t.id === "notify_custom")
            expect(customTask.type).toBe("io.kestra.plugin.core.log.Log")
            expect(customTask.message).toContain("trigger.executionId")
        })

        it("custom channel is always emitted even when no plugins are installed", () => {
            // Given
            const state = baseState()
            state.notify.slack = true
            state.notify.custom = true

            // When — only the Log fqcn is available, so slack is filtered out
            const yaml = recipeToYaml(state, "system", new Set(["io.kestra.plugin.core.log.Log"]))

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            expect(parsed.tasks.find((t: any) => t.id === "notify_slack")).toBeUndefined()
            expect(parsed.tasks.find((t: any) => t.id === "notify_custom")).toBeDefined()
        })
    })

    describe("webhook trigger", () => {
        it("generates webhook trigger with key", () => {
            // Given
            const state = baseState()
            state.triggerType = "webhook"
            state.webhookKey = "test-key-123"
            state.notify.slack = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            const trigger = parsed.triggers[0]
            expect(trigger.type).toBe("io.kestra.plugin.core.trigger.Webhook")
            expect(trigger.key).toBe("test-key-123")
        })

        it("uses IncomingWebhook variant for webhook trigger", () => {
            // Given
            const state = baseState()
            state.triggerType = "webhook"
            state.notify.teams = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            const teamsTask = parsed.tasks.find((t: any) => t.id === "notify_teams")
            expect(teamsTask.type).toBe("io.kestra.plugin.microsoft365.teams.TeamsIncomingWebhook")
        })
    })

    describe("other trigger", () => {
        it("generates other trigger with provided fqcn", () => {
            // Given
            const state = baseState()
            state.triggerType = "other"
            state.otherTriggerType = "io.kestra.plugin.core.trigger.Polling"
            state.notify.email = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            expect(parsed.triggers[0].type).toBe("io.kestra.plugin.core.trigger.Polling")
        })

        it("produces empty triggers array when otherTriggerType is not set", () => {
            // Given
            const state = baseState()
            state.triggerType = "other"
            state.otherTriggerType = ""
            state.notify.slack = true

            // When
            const flowObj = recipeToFlowObject(state, "system")

            // Then
            expect((flowObj.triggers as any[]).length).toBe(0)
        })
    })

    describe("FQCN availability filtering", () => {
        it("omits slack task when its FQCN is not in the available set", () => {
            // Given
            const state = baseState()
            state.notify.slack = true
            state.notify.email = true
            const available = new Set(["io.kestra.plugin.email.MailExecution"])

            // When
            const flowObj = recipeToFlowObject(state, "system", available)

            // Then
            const taskIds = (flowObj.tasks as any[]).map(t => t.id)
            expect(taskIds).not.toContain("notify_slack")
            expect(taskIds).toContain("notify_email")
        })

        it("includes all tasks when availableFqcns is empty (permissive mode)", () => {
            // Given
            const state = baseState()
            state.notify.slack = true
            state.notify.email = true

            // When
            const flowObj = recipeToFlowObject(state, "system", new Set())

            // Then
            const taskIds = (flowObj.tasks as any[]).map(t => t.id)
            expect(taskIds).toContain("notify_slack")
            expect(taskIds).toContain("notify_email")
        })
    })

    describe("round-trip stability", () => {
        it("parse(stringify(x)) produces the same content", () => {
            // Given
            const state = baseState()
            state.notify.slack = true

            // When
            const yaml = recipeToYaml(state, "system")
            const reparsed = flowYamlUtils.parse(yaml)
            const restringified = flowYamlUtils.stringify(reparsed)

            // Then
            const reparsedfinal = flowYamlUtils.parse(restringified)
            expect(reparsedfinal.namespace).toBe(reparsed.namespace)
            expect(reparsedfinal.triggers[0].type).toBe(reparsed.triggers[0].type)
        })

        it("uses the provided systemNamespace for the flow namespace", () => {
            // Given
            const state = baseState()
            state.notify.email = true

            // When
            const yaml = recipeToYaml(state, "custom-system")

            // Then
            const parsed = flowYamlUtils.parse(yaml)
            expect(parsed.namespace).toBe("custom-system")
        })

        it("uses SYSTEM_FLOW_RECIPE_ID as the flow id", () => {
            // Given
            const state = baseState()
            state.notify.email = true

            // When
            const flowObj = recipeToFlowObject(state, "system")

            // Then
            expect(flowObj.id).toBe(SYSTEM_FLOW_RECIPE_ID)
        })
    })
    describe("notify task properties match the plugin schemas", () => {
        const notifyTask = (state: RecipeState, id: string) =>
            flowYamlUtils.parse(recipeToYaml(state, "system")).tasks.find((t: any) => t.id === id)

        it("only sets slack channel where SlackTemplate declares it", () => {
            const execution = baseState()
            execution.notify.slack = true
            expect(notifyTask(execution, "notify_slack").channel).toBe("#alerts")

            const webhook = baseState()
            webhook.triggerType = "webhook"
            webhook.notify.slack = true
            const webhookTask = notifyTask(webhook, "notify_slack")
            expect(webhookTask).not.toHaveProperty("channel")
            expect(Object.keys(webhookTask)).toEqual(
                expect.arrayContaining(["id", "type", "url", "messageText"]),
            )
        })

        it("sends a teams card payload rather than an undeclared message property", () => {
            const state = baseState()
            state.triggerType = "schedule"
            state.notify.teams = true

            const task = notifyTask(state, "notify_teams")
            expect(task.message).toBeUndefined()
            expect(JSON.parse(task.payload)["@type"]).toBe("MessageCard")
        })

        it("sends the teams execution id rather than a payload on execution triggers", () => {
            const state = baseState()
            state.notify.teams = true

            const task = notifyTask(state, "notify_teams")
            expect(task.payload).toBeUndefined()
            expect(task.executionId).toContain("trigger.executionId")
        })

        it("declares a single-string recipient and an smtp host on mail tasks", () => {
            const state = baseState()
            state.notify.email = true
            state.emailTo = "ops@example.com"

            const task = notifyTask(state, "notify_email")
            expect(task.to).toBe("ops@example.com")
            expect(Array.isArray(task.to)).toBe(false)
            expect(task.host).toContain("EMAIL_HOST")
            expect(task.port).toBe(465)
        })
    })
})
