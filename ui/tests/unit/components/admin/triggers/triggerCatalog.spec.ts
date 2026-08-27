import {describe, test, expect} from "vitest"

import {triggerDisplayName, isMcpTrigger, MCP_TOOL_TYPE} from "../../../../../src/components/admin/triggers/triggerCatalog"

describe("triggerDisplayName", () => {
    test("uses the trigger's own name when it isn't the generic class name", () => {
        expect(triggerDisplayName({
            type: "io.kestra.plugin.core.trigger.Schedule",
            name: "Schedule",
            pluginTitle: "core Trigger",
            group: "core",
        })).toBe("Schedule")
    })

    test("falls back to the plugin's own declared title when the class is named `Trigger`", () => {
        expect(triggerDisplayName({
            type: "io.kestra.plugin.mongodb.Trigger",
            name: "Trigger",
            pluginTitle: "MongoDB",
            group: "app",
        })).toBe("MongoDB")
    })

    test("disambiguates two unrelated plugins that share the same last package segment", () => {
        // io.kestra.plugin.mongodb.Trigger and io.kestra.plugin.debezium.mongodb.Trigger both end
        // in "mongodb" and both use the conventional `Trigger` class name: a package-derived guess
        // would collide the two under the same label. Each plugin's own declared title (surfaced by
        // the backend, not parsed from the class package) must keep them distinct and correctly cased.
        const mongodb = triggerDisplayName({
            type: "io.kestra.plugin.mongodb.Trigger",
            name: "Trigger",
            pluginTitle: "MongoDB",
            group: "app",
        })
        const debeziumMongodb = triggerDisplayName({
            type: "io.kestra.plugin.debezium.mongodb.Trigger",
            name: "Trigger",
            pluginTitle: "Debezium MongoDB",
            group: "app",
        })

        expect(mongodb).toBe("MongoDB")
        expect(debeziumMongodb).toBe("Debezium MongoDB")
        expect(mongodb).not.toBe(debeziumMongodb)
    })

    test("tells apart the polling and realtime triggers of a same plugin", () => {
        // A plugin ships both, named `Trigger` and `RealtimeTrigger`, so the plugin title alone
        // would render the two cards identically.
        const polling = triggerDisplayName({
            type: "io.kestra.plugin.debezium.mongodb.Trigger",
            name: "Trigger",
            pluginTitle: "Debezium MongoDB",
            group: "app",
        })
        const realtime = triggerDisplayName({
            type: "io.kestra.plugin.debezium.mongodb.RealtimeTrigger",
            name: "RealtimeTrigger",
            pluginTitle: "Debezium MongoDB",
            group: "realtime",
        })

        expect(polling).toBe("Debezium MongoDB")
        expect(realtime).toBe("Debezium MongoDB Realtime")
    })

    test("keeps same-named classes of different plugins apart through their class name", () => {
        // Every scripts plugin ships a `CommandsTrigger`, so the class name alone collides five ways.
        expect(triggerDisplayName({
            type: "io.kestra.plugin.scripts.python.CommandsTrigger",
            name: "CommandsTrigger",
            pluginTitle: "Python",
            group: "app",
        })).toBe("Python Commands")
        expect(triggerDisplayName({
            type: "io.kestra.plugin.scripts.node.CommandsTrigger",
            name: "CommandsTrigger",
            pluginTitle: "Node",
            group: "app",
        })).toBe("Node Commands")
    })

    test("does not prefix a core trigger with the plugin title it carries", () => {
        // Core triggers resolve to a plugin title of their own ("core Trigger"), which would only
        // restate the category the card already shows as a tag.
        expect(triggerDisplayName({
            type: "io.kestra.plugin.core.trigger.Webhook",
            name: "Webhook",
            pluginTitle: "core Trigger",
            group: "core",
        })).toBe("Webhook")
    })

    test("falls back to the short class name when neither name nor pluginTitle is usable", () => {
        expect(triggerDisplayName({
            type: "io.kestra.plugin.unknown.Trigger",
            name: "Trigger",
            pluginTitle: "",
            group: "app",
        })).toBe("Trigger")
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
