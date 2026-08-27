import {describe, test, expect} from "vitest"

import {triggerDisplayName, isMcpTrigger, MCP_TOOL_TYPE} from "../../../../../src/components/admin/triggers/triggerCatalog"

describe("triggerDisplayName", () => {
    test("uses the trigger's own name when it isn't the generic class name", () => {
        expect(triggerDisplayName({
            type: "io.kestra.plugin.core.trigger.Schedule",
            name: "Schedule",
            pluginTitle: "core",
        })).toBe("Schedule")
    })

    test("falls back to the plugin's own declared title when the class is named `Trigger`", () => {
        expect(triggerDisplayName({
            type: "io.kestra.plugin.mongodb.Trigger",
            name: "Trigger",
            pluginTitle: "MongoDB",
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
        })
        const debeziumMongodb = triggerDisplayName({
            type: "io.kestra.plugin.debezium.mongodb.Trigger",
            name: "Trigger",
            pluginTitle: "Debezium MongoDB",
        })

        expect(mongodb).toBe("MongoDB")
        expect(debeziumMongodb).toBe("Debezium MongoDB")
        expect(mongodb).not.toBe(debeziumMongodb)
    })

    test("falls back to the short class name when neither name nor pluginTitle is usable", () => {
        expect(triggerDisplayName({
            type: "io.kestra.plugin.unknown.Trigger",
            name: "Trigger",
            pluginTitle: "",
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
