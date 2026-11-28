import {describe, expect, test} from "vitest"
import {diffFlowSources, mergeFlowGraphs} from "./flowDiff"
import type {FlowGraph} from "./vueFlowUtils"

const ORDERS_BEFORE = `id: orders_sync
namespace: company.sales
tasks:
  - id: extract
    type: io.kestra.plugin.core.http.Download
    uri: https://shop.example.com/orders
  - id: validate
    type: io.kestra.plugin.scripts.python.Script
    script: print(1)
  - id: load
    type: io.kestra.plugin.jdbc.postgresql.CopyIn
    table: orders
    timeout: PT10M
  - id: notify_slack
    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
    url: https://hooks.slack.com/x
triggers:
  - id: on_order
    type: io.kestra.plugin.core.trigger.Webhook
    key: orders
`

const ORDERS_AFTER = `id: orders_sync
namespace: company.sales
tasks:
  - id: extract
    type: io.kestra.plugin.core.http.Download
    uri: https://shop.example.com/orders
  - id: validate
    type: io.kestra.plugin.scripts.python.Script
    script: print(1)
  - id: dedupe
    type: io.kestra.plugin.scripts.python.Script
    script: print(2)
  - id: load
    type: io.kestra.plugin.jdbc.postgresql.CopyIn
    table: orders
    timeout: PT30M
    retry:
      type: constant
      maxAttempts: 3
  - id: notify_teams
    type: io.kestra.plugin.notifications.teams.TeamsIncomingWebhook
    url: https://teams.example.com/x
triggers:
  - id: on_order
    type: io.kestra.plugin.core.trigger.Webhook
    key: orders
`

const statusOf = (diff: ReturnType<typeof diffFlowSources>) => Object.fromEntries(
    diff.tasks.map((task) => [task.id, task.status]),
)

describe("diffFlowSources", () => {
    test("sorts every task and trigger into added, removed, changed or unchanged", () => {
        const diff = diffFlowSources(ORDERS_BEFORE, ORDERS_AFTER)

        expect(statusOf(diff)).toEqual({
            on_order: "unchanged",
            extract: "unchanged",
            validate: "unchanged",
            dedupe: "added",
            load: "changed",
            notify_slack: "removed",
            notify_teams: "added",
        })
        expect(diff.counts).toMatchObject({added: 2, removed: 1, changed: 1, unchanged: 3})
    })

    test("lists only the properties that changed, with their value before and after", () => {
        const load = diffFlowSources(ORDERS_BEFORE, ORDERS_AFTER).tasks.find((task) => task.id === "load")

        expect(load?.changes).toEqual([
            {key: "timeout", before: "PT10M", after: "PT30M"},
            {key: "retry", before: undefined, after: {type: "constant", maxAttempts: 3}},
        ])
    })

    test("keeps a removed task next to the task it used to follow", () => {
        const ids = diffFlowSources(ORDERS_BEFORE, ORDERS_AFTER).tasks.map((task) => task.id)

        expect(ids.indexOf("notify_slack")).toBe(ids.indexOf("load") + 1)
    })

    test("reads an id change with the same type and properties as a rename", () => {
        const after = ORDERS_BEFORE.replace("id: extract", "id: fetch_orders")
        const fetch = diffFlowSources(ORDERS_BEFORE, after).tasks.find((task) => task.id === "fetch_orders")

        expect(fetch).toMatchObject({status: "renamed", renamedFrom: "extract"})
        expect(diffFlowSources(ORDERS_BEFORE, after).tasks.some((task) => task.id === "extract")).toBe(false)
    })

    test("reads a task that changed block as moved, and still lists its property changes", () => {
        const before = `id: f
namespace: n
tasks:
  - id: gate
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: publish
        type: io.kestra.plugin.core.log.Log
        message: publish
  - id: report
    type: io.kestra.plugin.core.log.Log
    message: report
`
        const after = `id: f
namespace: n
tasks:
  - id: gate
    type: io.kestra.plugin.core.flow.If
    condition: "{{ false }}"
    then:
      - id: publish
        type: io.kestra.plugin.core.log.Log
        message: publish
      - id: report
        type: io.kestra.plugin.core.log.Log
        message: weekly report
`
        const diff = diffFlowSources(before, after)

        expect(diff.tasks.find((task) => task.id === "report")).toMatchObject({
            status: "moved",
            previousSection: "tasks",
            section: "gate.then",
            changes: [{key: "message", before: "report", after: "weekly report"}],
        })
        expect(diff.tasks.find((task) => task.id === "gate")).toMatchObject({
            status: "changed",
            changes: [{key: "condition", before: "{{ true }}", after: "{{ false }}"}],
        })
    })

    test("finds the tasks of a Switch, whose branches are a map of task lists", () => {
        const flow = (message: string) => `id: f
namespace: n
tasks:
  - id: route
    type: io.kestra.plugin.core.flow.Switch
    value: "{{ inputs.kind }}"
    cases:
      A:
        - id: handle_a
          type: io.kestra.plugin.core.log.Log
          message: ${message}
`
        const handle = diffFlowSources(flow("a"), flow("b")).tasks.find((task) => task.id === "handle_a")

        expect(handle).toMatchObject({status: "changed", section: "route.cases.A"})
        const route = diffFlowSources(flow("a"), flow("b")).tasks.find((task) => task.id === "route")
        expect(route?.status).toBe("unchanged")
    })

    test("treats a flow that does not exist on one side as all added or all removed", () => {
        const created = Object.values(statusOf(diffFlowSources(undefined, ORDERS_AFTER)))
        expect(created.every((status) => status === "added")).toBe(true)
        const deleted = Object.values(statusOf(diffFlowSources(ORDERS_BEFORE, undefined)))
        expect(deleted.every((status) => status === "removed")).toBe(true)
    })

    test("returns an empty diff for YAML it cannot parse instead of throwing", () => {
        expect(diffFlowSources("tasks: [", ORDERS_AFTER).counts.added).toBe(6)
    })

    test("compares a trigger and a task that share an id apart", () => {
        const diff = diffFlowSources(
            "tasks:\n  - {id: notify, type: t}\n",
            "tasks:\n  - {id: notify, type: t}\ntriggers:\n  - {id: notify, type: s}\n",
        )

        expect(diff.tasks.map((task) => [task.section, task.id, task.status])).toEqual([
            ["triggers", "notify", "added"],
            ["tasks", "notify", "unchanged"],
        ])
    })
})

describe("mergeFlowGraphs", () => {
    const node = (uid: string, id?: string) => ({
        uid,
        type: "io.kestra.core.models.hierarchies.GraphTask",
        ...(id ? {task: {id, type: "t"}} : {}),
    })
    const before: FlowGraph = {
        nodes: [
            node("root.start"),
            node("root.load", "load"),
            node("root.notify_slack", "notify_slack"),
            node("root.end"),
        ],
        edges: [
            {source: "root.start", target: "root.load"},
            {source: "root.load", target: "root.notify_slack"},
            {source: "root.notify_slack", target: "root.end"},
        ],
    }
    const after: FlowGraph = {
        nodes: [
            node("root.start"),
            node("root.load", "load"),
            node("root.notify_teams", "notify_teams"),
            node("root.end"),
        ],
        edges: [
            {source: "root.start", target: "root.load"},
            {source: "root.load", target: "root.notify_teams"},
            {source: "root.notify_teams", target: "root.end"},
        ],
    }
    const diff = diffFlowSources(
        "tasks:\n  - {id: load, type: t}\n  - {id: notify_slack, type: s}\n",
        "tasks:\n  - {id: load, type: t}\n  - {id: notify_teams, type: u}\n",
    )

    test("draws a removed task beside its replacement, with the edges it had", () => {
        const {graph} = mergeFlowGraphs(before, after, diff)

        expect(graph.nodes.map((n) => n.uid)).toEqual([
            "root.start",
            "root.load",
            "root.notify_teams",
            "root.end",
            "root.notify_slack",
        ])
        expect(graph.edges).toContainEqual({source: "root.load", target: "root.notify_slack"})
        expect(graph.edges).toContainEqual({source: "root.notify_slack", target: "root.end"})
        expect(graph.edges).toHaveLength(5)
    })

    test("gives each task node the status of its task", () => {
        const {statusByUid} = mergeFlowGraphs(before, after, diff)

        expect(Object.fromEntries(statusByUid)).toEqual({
            "root.load": "unchanged",
            "root.notify_teams": "added",
            "root.notify_slack": "removed",
        })
    })

    test("joins a removed task to the new graph's start and end dots, whose ids are random per graph", () => {
        const {graph} = mergeFlowGraphs(
            {
                nodes: [
                    node("root.root-Old1"),
                    node("root.load", "load"),
                    node("root.notify_slack", "notify_slack"),
                    node("root.end-Old2"),
                ],
                edges: [
                    {source: "root.load", target: "root.notify_slack"},
                    {source: "root.notify_slack", target: "root.end-Old2"},
                ],
            },
            {
                nodes: [
                    node("root.root-New1"),
                    node("root.load", "load"),
                    node("root.notify_teams", "notify_teams"),
                    node("root.end-New2"),
                ],
                edges: [
                    {source: "root.load", target: "root.notify_teams"},
                    {source: "root.notify_teams", target: "root.end-New2"},
                ],
            },
            diff,
        )

        expect(graph.edges).toContainEqual({source: "root.notify_slack", target: "root.end-New2"})
        expect(graph.nodes.map((n) => n.uid)).not.toContain("root.end-Old2")
    })

    test("links a removed task to the task before the one that moved away from in front of it", () => {
        const moved = diffFlowSources(
            "tasks:\n  - {id: load, type: t}\n  - {id: report, type: t}\n  - {id: notify, type: s}\n",
            [
                "tasks:",
                "  - {id: load, type: t}",
                "  - id: gate",
                "    type: If",
                "    then:",
                "      - {id: report, type: t}",
                "",
            ].join("\n"),
        )
        const {graph} = mergeFlowGraphs(
            {
                nodes: [
                    node("root.load", "load"),
                    node("root.report", "report"),
                    node("root.notify", "notify"),
                ],
                edges: [
                    {source: "root.load", target: "root.report"},
                    {source: "root.report", target: "root.notify"},
                ],
            },
            {
                nodes: [node("root.load", "load"), node("root.gate.report", "report")],
                edges: [{source: "root.load", target: "root.gate.report"}],
            },
            moved,
        )

        expect(graph.edges).toContainEqual({source: "root.load", target: "root.notify"})
    })

    test("does not bring back the old node of a task that only moved", () => {
        const moved = diffFlowSources(
            [
                "tasks:",
                "  - {id: load, type: t}",
                "  - id: gate",
                "    type: If",
                "    then:",
                "      - {id: a, type: t}",
                "",
            ].join("\n"),
            [
                "tasks:",
                "  - id: gate",
                "    type: If",
                "    then:",
                "      - {id: a, type: t}",
                "      - {id: load, type: t}",
                "",
            ].join("\n"),
        )
        const {graph} = mergeFlowGraphs(
            {
                nodes: [node("root.load", "load"), node("root.gate.a", "a")],
                edges: [{source: "root.load", target: "root.gate.a"}],
            },
            {
                nodes: [node("root.gate.a", "a"), node("root.gate.load", "load")],
                edges: [{source: "root.gate.a", target: "root.gate.load"}],
            },
            moved,
        )

        expect(graph.nodes.map((n) => n.uid)).toEqual(["root.gate.a", "root.gate.load"])
        expect(graph.edges).toHaveLength(1)
    })

    const trigger = (id: string) => ({
        uid: `root.Triggers.${id}`,
        type: "io.kestra.core.models.hierarchies.GraphTrigger",
        triggerDeclaration: {id},
    })
    const triggersBox = (...nodes: string[]) => ({
        cluster: {uid: "cluster_root.Triggers"},
        nodes,
        parents: [],
        start: nodes[0],
        end: nodes[1],
    })

    test("gives a trigger node the status of its trigger, not of a task with the same id", () => {
        const {statusByUid} = mergeFlowGraphs(
            {nodes: [node("root.notify", "notify")], edges: []},
            {nodes: [trigger("notify"), node("root.notify", "notify")], edges: []},
            diffFlowSources(
                "tasks:\n  - {id: notify, type: t}\n",
                "tasks:\n  - {id: notify, type: t}\ntriggers:\n  - {id: notify, type: s}\n",
            ),
        )

        expect(Object.fromEntries(statusByUid)).toEqual({
            "root.Triggers.notify": "added",
            "root.notify": "unchanged",
        })
    })

    test("keeps the triggers box, with its dots and edges, when the last trigger is removed", () => {
        const {graph, statusByUid} = mergeFlowGraphs(
            {
                nodes: [
                    node("root.root-aaa"),
                    node("root.Triggers.root-bbb"),
                    node("root.Triggers.end-ccc"),
                    trigger("daily"),
                    node("root.load", "load"),
                ],
                clusters: [triggersBox("root.Triggers.root-bbb", "root.Triggers.end-ccc", "root.Triggers.daily")],
                edges: [
                    {source: "root.Triggers.root-bbb", target: "root.Triggers.daily"},
                    {source: "root.Triggers.daily", target: "root.Triggers.end-ccc"},
                    {source: "root.Triggers.end-ccc", target: "root.root-aaa"},
                    {source: "root.root-aaa", target: "root.load"},
                ],
            },
            {
                nodes: [node("root.root-zzz"), node("root.load", "load")],
                edges: [{source: "root.root-zzz", target: "root.load"}],
            },
            diffFlowSources(
                "tasks:\n  - {id: load, type: t}\ntriggers:\n  - {id: daily, type: s}\n",
                "tasks:\n  - {id: load, type: t}\n",
            ),
        )

        expect(statusByUid.get("root.Triggers.daily")).toBe("removed")
        expect(graph.clusters?.map((cluster) => cluster.cluster.uid)).toEqual(["cluster_root.Triggers"])
        expect(graph.edges.map((edge) => `${edge.source} > ${edge.target}`)).toEqual([
            "root.root-zzz > root.load",
            "root.Triggers.root-bbb > root.Triggers.daily",
            "root.Triggers.daily > root.Triggers.end-ccc",
            "root.Triggers.end-ccc > root.root-zzz",
        ])
    })
})
