import {describe, test, expect} from "vitest"

import {buildTriggerDisplayNames, isMcpTrigger, MCP_TOOL_TYPE} from "../../../../../src/components/admin/triggers/triggerCatalog"

const label = (triggers: Parameters<typeof buildTriggerDisplayNames>[0], type: string) =>
    buildTriggerDisplayNames(triggers).get(type)

describe("buildTriggerDisplayNames", () => {
    test("keeps a catalog-unique name untouched, even a generic-looking one", () => {
        const triggers = [
            {type: "io.kestra.plugin.core.trigger.Schedule", name: "Schedule", pluginTitle: "core", pluginGroupTitle: "core"},
            {type: "io.kestra.plugin.ee.assets.EventTrigger", name: "EventTrigger", pluginTitle: "Assets", pluginGroupTitle: "Assets"},
        ]
        expect(label(triggers, "io.kestra.plugin.core.trigger.Schedule")).toBe("Schedule")
        expect(label(triggers, "io.kestra.plugin.ee.assets.EventTrigger")).toBe("EventTrigger")
    })

    test("labels the conventional `Trigger` and `RealtimeTrigger` classes with the plugin's declared title", () => {
        // Two unrelated plugins can share a package segment (io.kestra.plugin.mongodb vs
        // io.kestra.plugin.debezium.mongodb), so the context must be the declared title
        // surfaced by the backend, never a package-derived guess.
        const triggers = [
            {type: "io.kestra.plugin.mongodb.Trigger", name: "Trigger", pluginTitle: "MongoDB", pluginGroupTitle: "MongoDB"},
            {type: "io.kestra.plugin.debezium.mongodb.Trigger", name: "Trigger", pluginTitle: "Debezium MongoDB", pluginGroupTitle: "Debezium MongoDB"},
            {type: "io.kestra.plugin.kafka.RealtimeTrigger", name: "RealtimeTrigger", pluginTitle: "Kafka", pluginGroupTitle: "Apache Kafka"},
            {type: "io.kestra.plugin.aws.sqs.RealtimeTrigger", name: "RealtimeTrigger", pluginTitle: "SQS", pluginGroupTitle: "AWS"},
            {type: "io.kestra.plugin.aws.sqs.Trigger", name: "Trigger", pluginTitle: "SQS", pluginGroupTitle: "AWS"},
        ]
        expect(label(triggers, "io.kestra.plugin.mongodb.Trigger")).toBe("MongoDB")
        expect(label(triggers, "io.kestra.plugin.debezium.mongodb.Trigger")).toBe("Debezium MongoDB")
        expect(label(triggers, "io.kestra.plugin.kafka.RealtimeTrigger")).toBe("Kafka Realtime")
        // The realtime suffix keeps the card distinct from the same plugin's polling card.
        expect(label(triggers, "io.kestra.plugin.aws.sqs.RealtimeTrigger")).toBe("SQS Realtime")
        expect(label(triggers, "io.kestra.plugin.aws.sqs.Trigger")).toBe("SQS")
    })

    test("matches the realtime convention case-insensitively (`RealTimeTrigger`)", () => {
        const triggers = [
            {type: "io.kestra.plugin.azure.servicebus.RealTimeTrigger", name: "RealTimeTrigger", pluginTitle: "Service Bus", pluginGroupTitle: "Azure"},
            {type: "io.kestra.plugin.email.RealTimeTrigger", name: "RealTimeTrigger", pluginTitle: "Email", pluginGroupTitle: "Email"},
        ]
        expect(label(triggers, "io.kestra.plugin.azure.servicebus.RealTimeTrigger")).toBe("Service Bus Realtime")
        expect(label(triggers, "io.kestra.plugin.email.RealTimeTrigger")).toBe("Email Realtime")
    })

    test("disambiguates other class names shared across plugins, dropping the redundant `Trigger` suffix", () => {
        // The script plugins all declare `CommandsTrigger` and `ScriptTrigger` classes
        // (https://github.com/kestra-io/kestra/issues/16078, review round 2).
        const triggers = [
            {type: "io.kestra.plugin.scripts.node.CommandsTrigger", name: "CommandsTrigger", pluginTitle: "Node", pluginGroupTitle: "Node"},
            {type: "io.kestra.plugin.scripts.python.CommandsTrigger", name: "CommandsTrigger", pluginTitle: "Python", pluginGroupTitle: "Python"},
            {type: "io.kestra.plugin.scripts.node.ScriptTrigger", name: "ScriptTrigger", pluginTitle: "Node", pluginGroupTitle: "Node"},
            {type: "io.kestra.plugin.scripts.python.ScriptTrigger", name: "ScriptTrigger", pluginTitle: "Python", pluginGroupTitle: "Python"},
        ]
        expect(label(triggers, "io.kestra.plugin.scripts.node.CommandsTrigger")).toBe("Node Commands")
        expect(label(triggers, "io.kestra.plugin.scripts.python.CommandsTrigger")).toBe("Python Commands")
        expect(label(triggers, "io.kestra.plugin.scripts.node.ScriptTrigger")).toBe("Node Script")
        expect(label(triggers, "io.kestra.plugin.scripts.python.ScriptTrigger")).toBe("Python Script")
    })

    test("splits a CamelCase class name into words", () => {
        const triggers = [
            {type: "io.kestra.plugin.jira.IssueCreatedTrigger", name: "IssueCreatedTrigger", pluginTitle: "Jira", pluginGroupTitle: "Jira"},
            {type: "io.kestra.plugin.github.IssueCreatedTrigger", name: "IssueCreatedTrigger", pluginTitle: "GitHub", pluginGroupTitle: "GitHub"},
        ]
        expect(label(triggers, "io.kestra.plugin.jira.IssueCreatedTrigger")).toBe("Jira Issue Created")
        expect(label(triggers, "io.kestra.plugin.github.IssueCreatedTrigger")).toBe("GitHub Issue Created")
    })

    test("escalates to the plugin artifact's title when the subgroup title itself collides", () => {
        // Two subgroups of unrelated plugins can still declare the same title, so pluginTitle
        // alone renders two identical "core Realtime" cards (review round 2).
        const triggers = [
            {type: "io.kestra.plugin.nats.core.RealtimeTrigger", name: "RealtimeTrigger", pluginTitle: "core", pluginGroupTitle: "NATS"},
            {type: "io.kestra.plugin.datagen.core.RealtimeTrigger", name: "RealtimeTrigger", pluginTitle: "core", pluginGroupTitle: "Datagen"},
        ]
        expect(label(triggers, "io.kestra.plugin.nats.core.RealtimeTrigger")).toBe("NATS Realtime")
        expect(label(triggers, "io.kestra.plugin.datagen.core.RealtimeTrigger")).toBe("Datagen Realtime")
    })

    test("falls back to the raw name against an older backend that sends no titles", () => {
        const triggers = [
            {type: "io.kestra.plugin.kafka.RealtimeTrigger", name: "RealtimeTrigger", pluginTitle: ""},
            {type: "io.kestra.plugin.unknown.Trigger", name: "Trigger", pluginTitle: ""},
        ]
        expect(label(triggers, "io.kestra.plugin.kafka.RealtimeTrigger")).toBe("RealtimeTrigger")
        expect(label(triggers, "io.kestra.plugin.unknown.Trigger")).toBe("Trigger")
    })
})

describe("isMcpTrigger", () => {
    test("matches the canonical MCP tool trigger type", () => {
        expect(isMcpTrigger({type: MCP_TOOL_TYPE})).toBe(true)
    })

    test("matches any plugin's McpTool class", () => {
        expect(isMcpTrigger({type: "io.kestra.plugin.core.trigger.McpTool"})).toBe(true)
    })

    test("does not match unrelated triggers", () => {
        expect(isMcpTrigger({type: "io.kestra.plugin.mongodb.Trigger"})).toBe(false)
    })
})
