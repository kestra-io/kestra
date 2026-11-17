import {defineComponent, h, inject, markRaw, nextTick, ref, type Ref} from "vue"
import {mount} from "@vue/test-utils"
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"

import {TOPOLOGY_CLICK_INJECTION_KEY} from "../no-code/injectionKeys"
import type {TopologyClickParams} from "../no-code/utils/types"
import type {Panel} from "../../utils/multiPanelTypes"
import {NOCODE_PREFIX} from "./useNoCodePanels"

const flowStore = {flowYaml: ""}
const pluginsStore = {flowSchema: {$ref: "#/definitions/io.kestra.core.models.flows.Flow"}}

vi.mock("../../stores/flow", () => ({useFlowStore: () => flowStore}))
vi.mock("../../stores/plugins", () => ({usePluginsStore: () => pluginsStore}))

import {useTopologyPanels} from "./useTopologyPanels"

const FLOW_REF = "#/definitions/io.kestra.core.models.flows.Flow"

const FLOW = `id: my-flow
namespace: company.team
tasks:
  - id: first
    type: io.kestra.plugin.core.log.Log
  - id: second
    type: io.kestra.plugin.core.log.Log
`

const DAG_FIELD_FLOW = `id: my-flow
namespace: company.team
tasks:
  - dependency: first
    task:
      id: nested
      type: io.kestra.plugin.core.log.Log
`

const NESTED_FLOW = `id: my-flow
namespace: company.team
tasks:
  - id: parent
    type: io.kestra.plugin.core.flow.Sequential
    tasks:
      - id: child-a
        type: io.kestra.plugin.core.log.Log
      - id: child-b
        type: io.kestra.plugin.core.log.Log
`

const TRIGGER_FLOW = `id: my-flow
namespace: company.team
triggers:
  - id: hourly
    type: io.kestra.plugin.core.trigger.Schedule
    cron: "0 * * * *"
`

function panel(uids: string[]): Panel {
    const tabs = uids.map((uid) => ({
        uid,
        button: {label: uid, icon: markRaw(defineComponent({render: () => null}))},
        component: markRaw(defineComponent({render: () => null})),
    }))
    return {size: 50, tabs, activeTab: tabs[0]}
}

function mountTopologyPanels(panels: Panel[], flowYaml: string) {
    flowStore.flowYaml = flowYaml
    const panelsRef = ref<Panel[]>(panels)
    const openAddTaskTab = vi.fn()
    const openEditTaskTab = vi.fn()
    let topologyClick!: Ref<TopologyClickParams | undefined>

    const Child = defineComponent({
        setup() {
            topologyClick = inject(TOPOLOGY_CLICK_INJECTION_KEY)!
            return () => null
        },
    })

    mount(defineComponent({
        setup() {
            useTopologyPanels(panelsRef, openAddTaskTab, openEditTaskTab)
            return () => h(Child)
        },
    }))

    return {openAddTaskTab, openEditTaskTab, topologyClick}
}

describe("useTopologyPanels", () => {
    let warn: ReturnType<typeof vi.spyOn>

    beforeEach(() => {
        flowStore.flowYaml = ""
        pluginsStore.flowSchema = {$ref: FLOW_REF}
        warn = vi.spyOn(console, "warn").mockImplementation(() => {})
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    it("opens the add-task tab on the parent path when a create click targets an array element", async () => {
        const {openAddTaskTab, openEditTaskTab, topologyClick} = mountTopologyPanels([panel(["topology"])], FLOW)

        topologyClick.value = {action: "create", params: {section: "tasks", id: "second", position: "after"}}
        await nextTick()

        expect(openAddTaskTab).toHaveBeenCalledWith(
            {panelIndex: -1, tabIndex: -1},
            "tasks",
            `${FLOW_REF}/properties/tasks/items`,
            1,
            "after",
            undefined,
            1,
        )
        expect(openEditTaskTab).not.toHaveBeenCalled()
    })

    it("strips the field name and the index from a create click on a named Dag field", async () => {
        const {openAddTaskTab, openEditTaskTab, topologyClick} = mountTopologyPanels([panel(["topology"])], DAG_FIELD_FLOW)

        topologyClick.value = {action: "create", params: {section: "tasks", id: "nested", position: "before"}}
        await nextTick()

        expect(openAddTaskTab).toHaveBeenCalledWith(
            {panelIndex: -1, tabIndex: -1},
            "tasks",
            `${FLOW_REF}/properties/tasks/items`,
            0,
            "before",
            "task",
            1,
        )
        expect(openEditTaskTab).not.toHaveBeenCalled()
    })

    it("strips the index but keeps the parent path when an edit click has no field name", async () => {
        const {openAddTaskTab, openEditTaskTab, topologyClick} = mountTopologyPanels([panel(["topology"])], FLOW)

        topologyClick.value = {action: "edit", params: {section: "tasks", id: "second"}}
        await nextTick()

        expect(openEditTaskTab).toHaveBeenCalledWith(
            {panelIndex: -1, tabIndex: -1},
            "tasks",
            `${FLOW_REF}/properties/tasks/items`,
            1,
            1,
        )
        expect(openAddTaskTab).not.toHaveBeenCalled()
    })

    it("passes the full path without an index when an edit click targets a named Dag field", async () => {
        const {openAddTaskTab, openEditTaskTab, topologyClick} = mountTopologyPanels([panel(["topology"])], DAG_FIELD_FLOW)

        topologyClick.value = {action: "edit", params: {section: "tasks", id: "nested"}}
        await nextTick()

        expect(openEditTaskTab).toHaveBeenCalledWith(
            {panelIndex: -1, tabIndex: -1},
            "tasks[0].task",
            `${FLOW_REF}/properties/tasks/items`,
            undefined,
            1,
        )
        expect(openAddTaskTab).not.toHaveBeenCalled()
    })

    it("uses the innermost index when a create click targets a task nested in another task", async () => {
        const {openAddTaskTab, openEditTaskTab, topologyClick} = mountTopologyPanels([panel(["topology"])], NESTED_FLOW)

        topologyClick.value = {action: "create", params: {section: "tasks", id: "child-b", position: "after"}}
        await nextTick()

        expect(openAddTaskTab).toHaveBeenCalledWith(
            {panelIndex: -1, tabIndex: -1},
            "tasks[0].tasks",
            `${FLOW_REF}/properties/tasks/items`,
            1,
            "after",
            undefined,
            1,
        )
        expect(openEditTaskTab).not.toHaveBeenCalled()
    })

    it("uses the innermost index when an edit click targets a task nested in another task", async () => {
        const {openAddTaskTab, openEditTaskTab, topologyClick} = mountTopologyPanels([panel(["topology"])], NESTED_FLOW)

        topologyClick.value = {action: "edit", params: {section: "tasks", id: "child-b"}}
        await nextTick()

        expect(openEditTaskTab).toHaveBeenCalledWith(
            {panelIndex: -1, tabIndex: -1},
            "tasks[0].tasks",
            `${FLOW_REF}/properties/tasks/items`,
            1,
            1,
        )
        expect(openAddTaskTab).not.toHaveBeenCalled()
    })

    it("builds the block schema path from the flow schema ref, the clicked section and items", async () => {
        pluginsStore.flowSchema = {$ref: "#/definitions/acme.CustomFlow"}
        const {openEditTaskTab, topologyClick} = mountTopologyPanels([panel(["topology"])], TRIGGER_FLOW)

        topologyClick.value = {action: "edit", params: {section: "triggers", id: "hourly"}}
        await nextTick()

        expect(openEditTaskTab).toHaveBeenCalledWith(
            {panelIndex: -1, tabIndex: -1},
            "triggers",
            "#/definitions/acme.CustomFlow/properties/triggers/items",
            0,
            1,
        )
    })

    it("opens no editor when the click resolves to no path", async () => {
        const {openAddTaskTab, openEditTaskTab, topologyClick} = mountTopologyPanels([panel(["topology"])], TRIGGER_FLOW)

        topologyClick.value = {action: "create", params: {section: "tasks", id: "second", position: "after"}}
        await nextTick()

        expect(openAddTaskTab).not.toHaveBeenCalled()
        expect(openEditTaskTab).not.toHaveBeenCalled()
    })

    it("warns and opens no editor when the resolved path carries no index", async () => {
        const {openAddTaskTab, openEditTaskTab, topologyClick} = mountTopologyPanels([panel(["topology"])], FLOW)

        const click: TopologyClickParams = {action: "edit", params: {section: "tasks", id: "missing"}}
        topologyClick.value = click
        await nextTick()

        expect(warn).toHaveBeenCalledWith("No refPath found in topology click params", click)
        expect(openAddTaskTab).not.toHaveBeenCalled()
        expect(openEditTaskTab).not.toHaveBeenCalled()
    })

    it("asks for a new panel one slot after the topology panel when no no-code tab is open", async () => {
        const panels = [panel(["files"]), panel(["topology"]), panel(["edit"])]
        const {openAddTaskTab, topologyClick} = mountTopologyPanels(panels, FLOW)

        topologyClick.value = {action: "create", params: {section: "tasks", id: "second", position: "after"}}
        await nextTick()

        expect(openAddTaskTab).toHaveBeenCalledWith(
            {panelIndex: -1, tabIndex: -1},
            "tasks",
            `${FLOW_REF}/properties/tasks/items`,
            1,
            "after",
            undefined,
            2,
        )
    })

    it("asks for a new panel right after the first one when no topology panel is open", async () => {
        const {openAddTaskTab, topologyClick} = mountTopologyPanels([panel(["files"]), panel(["edit"])], FLOW)

        topologyClick.value = {action: "create", params: {section: "tasks", id: "second", position: "after"}}
        await nextTick()

        expect(openAddTaskTab).toHaveBeenCalledWith(
            {panelIndex: -1, tabIndex: -1},
            "tasks",
            `${FLOW_REF}/properties/tasks/items`,
            1,
            "after",
            undefined,
            1,
        )
    })

    it("reuses the existing no-code tab instead of opening a new panel", async () => {
        const panels = [
            panel(["files"]),
            panel(["topology"]),
            panel(["files", `${NOCODE_PREFIX}-0000-{"action":"create"}`, "edit"]),
        ]
        const {openEditTaskTab, topologyClick} = mountTopologyPanels(panels, FLOW)

        topologyClick.value = {action: "edit", params: {section: "tasks", id: "second"}}
        await nextTick()

        expect(openEditTaskTab).toHaveBeenCalledWith(
            {panelIndex: 2, tabIndex: 1},
            "tasks",
            `${FLOW_REF}/properties/tasks/items`,
            1,
            undefined,
        )
    })

    it("clears the click after handling it, without re-running and still accepting the next click", async () => {
        const {openAddTaskTab, topologyClick} = mountTopologyPanels([panel(["topology"])], FLOW)

        topologyClick.value = {action: "create", params: {section: "tasks", id: "first", position: "after"}}
        await nextTick()

        expect(topologyClick.value).toBeUndefined()
        expect(openAddTaskTab).toHaveBeenCalledTimes(1)

        await nextTick()
        expect(openAddTaskTab).toHaveBeenCalledTimes(1)

        topologyClick.value = {action: "create", params: {section: "tasks", id: "second", position: "after"}}
        await nextTick()

        expect(openAddTaskTab).toHaveBeenCalledTimes(2)
        expect(openAddTaskTab).toHaveBeenLastCalledWith(
            {panelIndex: -1, tabIndex: -1},
            "tasks",
            `${FLOW_REF}/properties/tasks/items`,
            1,
            "after",
            undefined,
            1,
        )
    })
})
