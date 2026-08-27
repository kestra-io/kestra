import {describe, test, expect} from "vitest"

import {triggerDisplayName, isMcpTrigger, MCP_TOOL_TYPE} from "../../../../../src/components/admin/triggers/triggerCatalog"

const KINDS: Record<string, string> = {
    triggers_add_kind_realtime: "Realtime",
    triggers_add_kind_app: "Polling",
}
const t = (key: string) => KINDS[key] ?? key

describe("triggerDisplayName", () => {
    test("uses the trigger's own name when it isn't the generic class name", () => {
        expect(triggerDisplayName({
            type: "io.kestra.plugin.core.trigger.Schedule",
            name: "Schedule",
            pluginTitle: "core Trigger",
            group: "core",
        }, t)).toBe("Schedule")
    })

    test("falls back to the plugin's own declared title when the class is named `Trigger`", () => {
        expect(triggerDisplayName({
            type: "io.kestra.plugin.mongodb.Trigger",
            name: "Trigger",
            pluginTitle: "MongoDB",
            group: "app",
        }, t)).toBe("MongoDB Polling")
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
        }, t)
        const debeziumMongodb = triggerDisplayName({
            type: "io.kestra.plugin.debezium.mongodb.Trigger",
            name: "Trigger",
            pluginTitle: "Debezium MongoDB",
            group: "app",
        }, t)

        expect(mongodb).toBe("MongoDB Polling")
        expect(debeziumMongodb).toBe("Debezium MongoDB Polling")
        expect(mongodb).not.toBe(debeziumMongodb)
    })

    test("tells apart the polling and realtime triggers of a same plugin", () => {
        // A plugin ships both, and the classes are named `Trigger` and `RealtimeTrigger`, so the
        // plugin title alone would render the two cards identically.
        const polling = triggerDisplayName({
            type: "io.kestra.plugin.debezium.mongodb.Trigger",
            name: "Trigger",
            pluginTitle: "Debezium MongoDB",
            group: "app",
        }, t)
        const realtime = triggerDisplayName({
            type: "io.kestra.plugin.debezium.mongodb.RealtimeTrigger",
            name: "RealtimeTrigger",
            pluginTitle: "Debezium MongoDB",
            group: "realtime",
        }, t)

        expect(polling).toBe("Debezium MongoDB Polling")
        expect(realtime).toBe("Debezium MongoDB Realtime")
    })

    test("keeps same-named classes of different plugins apart through their class name", () => {
        // Every scripts plugin ships a `CommandsTrigger`, so the class name alone collides five ways.
        expect(triggerDisplayName({
            type: "io.kestra.plugin.scripts.python.CommandsTrigger",
            name: "CommandsTrigger",
            pluginTitle: "Python",
            group: "app",
        }, t)).toBe("Python Commands")
        expect(triggerDisplayName({
            type: "io.kestra.plugin.scripts.node.CommandsTrigger",
            name: "CommandsTrigger",
            pluginTitle: "Node",
            group: "app",
        }, t)).toBe("Node Commands")
    })

    test("looks up no kind key for a core trigger, which has none", () => {
        const forbidden = () => {
            throw new Error("the core category has no kind key")
        }

        expect(triggerDisplayName({
            type: "io.kestra.plugin.core.trigger.Trigger",
            name: "Trigger",
            pluginTitle: "core Trigger",
            group: "core",
        }, forbidden)).toBe("Trigger")
    })

    test("falls back to the short class name when neither name nor pluginTitle is usable", () => {
        expect(triggerDisplayName({
            type: "io.kestra.plugin.unknown.Trigger",
            name: "Trigger",
            pluginTitle: "",
            group: "app",
        }, t)).toBe("Trigger")
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
