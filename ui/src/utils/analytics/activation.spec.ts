import {describe, expect, it} from "vitest"
import {executionLocation, flowTaskStats, isExampleFlow, primaryTriggerType, routeSection} from "./activation"

describe("isExampleFlow", () => {
    it("flags the tutorial namespace", () => {
        expect(isExampleFlow("tutorial")).toBe(true)
    })

    it.each(["tutorials", "my.tutorial", "company.data", undefined])("does not flag %s", (namespace) => {
        expect(isExampleFlow(namespace)).toBe(false)
    })
})

describe("executionLocation", () => {
    it("reports playground from the execution kind, whatever the route", () => {
        expect(executionLocation("flows/list", "PLAYGROUND")).toEqual("playground")
    })

    it.each([
        ["flows/update/edit", "flow_editor"],
        ["flows/update/executions", "flow_editor"],
        ["flows/create", "flow_editor"],
        ["flows/list", "flow_list"],
        ["flows/search", "flow_list"],
        ["executions/list", "executions_view"],
        ["executions/update/gantt", "executions_view"],
    ])("maps %s to %s", (routeName, expected) => {
        expect(executionLocation(routeName, "NORMAL")).toEqual(expected)
    })

    it("falls back to the first route segment for an unmapped route", () => {
        expect(executionLocation("admin/triggers", "NORMAL")).toEqual("admin")
    })

    it("returns undefined without a route name", () => {
        expect(executionLocation(undefined, "NORMAL")).toBeUndefined()
    })
})

describe("routeSection", () => {
    it.each([
        ["flows/list", "flows"],
        ["admin/instance/worker-queues", "admin"],
        ["home", "home"],
    ])("maps %s to %s", (routeName, expected) => {
        expect(routeSection(routeName)).toEqual(expected)
    })

    it.each([undefined, ""])("returns undefined for %j", (routeName) => {
        expect(routeSection(routeName)).toBeUndefined()
    })
})

describe("flowTaskStats", () => {
    it("counts nested tasks and distinct plugins", () => {
        const tasks = [
            {type: "io.kestra.plugin.core.flow.Sequential", tasks: [
                {type: "io.kestra.plugin.scripts.python.Script"},
                {type: "io.kestra.plugin.scripts.python.Script"},
            ]},
            {type: "io.kestra.plugin.core.log.Log"},
        ]

        expect(flowTaskStats(tasks)).toEqual({taskCount: 4, pluginCount: 3})
    })

    it("returns zeroes without tasks", () => {
        expect(flowTaskStats(undefined)).toEqual({taskCount: 0, pluginCount: 0})
    })
})

describe("primaryTriggerType", () => {
    it.each([
        [undefined, "manual"],
        [[], "manual"],
        [[{type: "io.kestra.plugin.core.trigger.Schedule"}], "cron"],
        [[{type: "io.kestra.plugin.core.trigger.ScheduleOnDates"}], "cron"],
        [[{type: "io.kestra.plugin.core.trigger.Webhook"}], "webhook"],
        [[{type: "io.kestra.plugin.core.trigger.Flow"}], "flow"],
        [[{type: "io.kestra.plugin.core.trigger.McpToolTrigger"}], "other"],
    ])("maps %j to %s", (triggers, expected) => {
        expect(primaryTriggerType(triggers)).toEqual(expected)
    })

    // 78 plugin triggers share the short name `Trigger`, 23 share `RealtimeTrigger`.
    it.each([
        "io.kestra.plugin.kafka.Trigger",
        "io.kestra.plugin.jdbc.postgresql.Trigger",
        "io.kestra.plugin.aws.sqs.RealtimeTrigger",
    ])("buckets the plugin trigger %s as other", (type) => {
        expect(primaryTriggerType([{type}])).toEqual("other")
    })

    it("reads the first trigger when several are declared", () => {
        expect(primaryTriggerType([
            {type: "io.kestra.plugin.core.trigger.Webhook"},
            {type: "io.kestra.plugin.core.trigger.Schedule"},
        ])).toEqual("webhook")
    })
})
