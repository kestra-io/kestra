import {describe, expect, it} from "vitest"
import cronstrue from "cronstrue"
import {State} from "@kestra-io/design-system"
import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"
import {
    DEFAULT_CRON,
    DEFAULT_EMAIL_TO,
    DEFAULT_SLACK_CHANNEL,
    DEFAULT_STATES,
    DEFAULT_WEBHOOK_KEY,
    notifyTaskFqcn,
    recipeToFlowObject,
    recipeToYaml,
    SYSTEM_FLOW_RECIPE_ID,
    type RecipeState,
    type TriggerType,
} from "./recipeToYaml"
import {parseBlock} from "../../tests/unit/utils/parsedBlock"

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
            const parsed = parseBlock(yaml)
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
            const parsed = parseBlock(yaml)
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
            const parsed = parseBlock(yaml)
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
            const parsed = parseBlock(yaml)
            expect(parsed.triggers[0].conditions).toBeUndefined()
        })

        it("uses SlackExecution task (not IncomingWebhook) for execution trigger", () => {
            // Given
            const state = baseState()
            state.notify.slack = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = parseBlock(yaml)
            const slackTask = parsed.tasks.find((t: any) => t.id === "notify_slack")
            expect(slackTask?.type).toBe("io.kestra.plugin.slack.notifications.SlackExecution")
            expect(slackTask?.executionId).toBe("{{ trigger.executionId }}")
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
            const parsed = parseBlock(yaml)
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
            const parsed = parseBlock(yaml)
            const emailTask = parsed.tasks.find((t: any) => t.id === "notify_email")
            expect(emailTask?.type).toBe("io.kestra.plugin.email.MailExecution")
            expect(emailTask?.executionId).toContain("trigger.executionId")
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
            const parsed = parseBlock(yaml)
            const slackTask = parsed.tasks.find((t: any) => t.id === "notify_slack")
            expect(slackTask?.type).toBe("io.kestra.plugin.slack.notifications.SlackIncomingWebhook")
            expect(slackTask?.executionId).toBeUndefined()
        })

        it("slack IncomingWebhook sends messageText, not a channel it cannot honour", () => {
            // Given
            const state = baseState()
            state.triggerType = "schedule"
            state.notify.slack = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = parseBlock(yaml)
            const slackTask = parsed.tasks.find((t: any) => t.id === "notify_slack")
            expect(slackTask?.messageText).toContain("flow.id")
            expect(slackTask?.channel).toBeUndefined()
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
            const parsed = parseBlock(yaml)
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
            const parsed = parseBlock(yaml)
            const emailTask = parsed.tasks.find((t: any) => t.id === "notify_email")
            expect(emailTask?.type).toBe("io.kestra.plugin.email.MailSend")
            expect(emailTask?.executionId).toBeUndefined()
            expect(emailTask?.htmlTextContent).not.toContain("trigger.executionId")
        })

        it("custom channel adds an editable Log placeholder task", () => {
            // Given
            const state = baseState()
            state.notify.custom = true

            // When
            const yaml = recipeToYaml(state, "system")

            // Then
            const parsed = parseBlock(yaml)
            const customTask = parsed.tasks.find((t: any) => t.id === "notify_custom")
            expect(customTask?.type).toBe("io.kestra.plugin.core.log.Log")
            expect(customTask?.message).toContain("trigger.executionId")
        })

        it("custom channel is always emitted even when no plugins are installed", () => {
            // Given
            const state = baseState()
            state.notify.slack = true
            state.notify.custom = true

            // When — only the Log fqcn is available, so slack is filtered out
            const yaml = recipeToYaml(state, "system", new Set(["io.kestra.plugin.core.log.Log"]))

            // Then
            const parsed = parseBlock(yaml)
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
            const parsed = parseBlock(yaml)
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
            const parsed = parseBlock(yaml)
            const teamsTask = parsed.tasks.find((t: any) => t.id === "notify_teams")
            expect(teamsTask?.type).toBe("io.kestra.plugin.microsoft365.teams.TeamsIncomingWebhook")
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
            const parsed = parseBlock(yaml)
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
            const reparsed = parseBlock(yaml)
            const restringified = flowYamlUtils.stringify(reparsed)

            // Then
            const reparsedfinal = parseBlock(restringified)
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
            const parsed = parseBlock(yaml)
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
        const notifyTask = (state: RecipeState, id: string) => {
            const task = parseBlock(recipeToYaml(state, "system")).tasks.find((t: any) => t.id === id)
            if (!task) throw new Error(`The generated flow has no task "${id}".`)
            return task
        }

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
            expect(JSON.parse(String(task.payload))["@type"]).toBe("MessageCard")
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

    describe("notifyTaskFqcn", () => {
        it.each([
            ["slack", true, "io.kestra.plugin.slack.notifications.SlackExecution"],
            ["slack", false, "io.kestra.plugin.slack.notifications.SlackIncomingWebhook"],
            ["teams", true, "io.kestra.plugin.microsoft365.teams.TeamsExecution"],
            ["teams", false, "io.kestra.plugin.microsoft365.teams.TeamsIncomingWebhook"],
            ["email", true, "io.kestra.plugin.email.MailExecution"],
            ["email", false, "io.kestra.plugin.email.MailSend"],
        ] as const)("returns the %s class for executionTrigger=%s", (channel, isExecutionTrigger, fqcn) => {
            expect(notifyTaskFqcn(channel, isExecutionTrigger)).toBe(fqcn)
        })

        it.each(["custom", "discord", ""])("throws rather than returning undefined for the channel %j", (channel) => {
            expect(() => notifyTaskFqcn(channel as never, true)).toThrow(TypeError)
            expect(() => notifyTaskFqcn(channel as never, false)).toThrow(TypeError)
        })
    })

    describe("defaults", () => {
        it("uses a five-field cron expression that cronstrue can describe", () => {
            expect(DEFAULT_CRON.split(" ")).toHaveLength(5)
            expect(() => cronstrue.toString(DEFAULT_CRON)).not.toThrow()
        })

        it("uses a webhook key that fits in a URL path segment unescaped", () => {
            expect(DEFAULT_WEBHOOK_KEY).toMatch(/^[A-Za-z0-9_-]+$/)
            expect(encodeURIComponent(DEFAULT_WEBHOOK_KEY)).toBe(DEFAULT_WEBHOOK_KEY)
        })

        it("uses a slack channel name starting with # and without spaces", () => {
            expect(DEFAULT_SLACK_CHANNEL).toMatch(/^#[a-z0-9_-]+$/)
        })

        it("uses a single email address as recipient", () => {
            expect(DEFAULT_EMAIL_TO).toMatch(/^[^\s@,;]+@[^\s@,;]+\.[^\s@,;]+$/)
        })

        it("lists only real execution states, without duplicates", () => {
            const stateNames = State.arrayAllStates().map(state => state.name)

            expect(DEFAULT_STATES.length).toBeGreaterThan(0)
            expect(new Set(DEFAULT_STATES).size).toBe(DEFAULT_STATES.length)
            expect(stateNames).toEqual(expect.arrayContaining(DEFAULT_STATES))
        })

        it.each(["execution", "schedule", "webhook"] as TriggerType[])(
            "generates a parseable %s recipe that falls back to the defaults for every field left empty",
            (triggerType) => {
                const state: RecipeState = {
                    ...baseState(),
                    triggerType,
                    states: [],
                    cron: "",
                    timezone: "",
                    webhookKey: "",
                    slackChannel: "",
                    emailTo: "",
                    notify: {slack: true, teams: true, email: true, custom: true},
                }

                const yaml = recipeToYaml(state, "system")

                expect(yaml).not.toContain("undefined")
                const parsed = parseBlock(yaml)
                expect(parsed.id).toBe(SYSTEM_FLOW_RECIPE_ID)
                expect(parsed.tasks.map(task => task.id))
                    .toEqual(["notify_slack", "notify_teams", "notify_email", "notify_custom"])
                expect(parsed.tasks.find(task => task.id === "notify_email")?.to).toBe(DEFAULT_EMAIL_TO)

                const trigger = parsed.triggers[0]
                if (triggerType === "execution") {
                    expect(trigger.states).toEqual(DEFAULT_STATES)
                    expect(parsed.tasks.find(task => task.id === "notify_slack")?.channel).toBe(DEFAULT_SLACK_CHANNEL)
                } else if (triggerType === "schedule") {
                    expect(trigger.cron).toBe(DEFAULT_CRON)
                    expect(trigger.timezone).toBeUndefined()
                } else {
                    expect(trigger.key).toBe(DEFAULT_WEBHOOK_KEY)
                }
            },
        )
    })
})
