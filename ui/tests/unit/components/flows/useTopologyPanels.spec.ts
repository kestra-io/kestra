import {defineComponent, h, inject, nextTick, ref} from "vue"
import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"

vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({flowYaml: FLOW_YAML}),
}))

vi.mock("../../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({flowSchema: {$ref: "#/definitions/io.kestra.core.models.flows.Flow"}}),
}))

import {useTopologyPanels} from "../../../../src/components/flows/useTopologyPanels"
import {TOPOLOGY_CLICK_INJECTION_KEY} from "../../../../src/components/no-code/injectionKeys"
import {TopologyClickParams} from "../../../../src/components/no-code/utils/types"
import {Panel} from "../../../../src/utils/multiPanelTypes"

let FLOW_YAML = ""

const FLOW_WITHOUT_ERRORS = `
id: my-flow
namespace: company.team
tasks:
  - id: first
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
`.trim()

const FLOW_WITH_ERRORS = `
id: my-flow
namespace: company.team
tasks:
  - id: first
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    errors:
      - id: handler_one
        type: io.kestra.plugin.core.log.Log
      - id: handler_two
        type: io.kestra.plugin.core.log.Log
`.trim()

function mountTopologyPanels() {
    const panels = ref<Panel[]>([{tabs: [{uid: "topology"}], activeTab: {uid: "topology"}}] as unknown as Panel[])
    const openAddTaskTab = vi.fn()
    const openEditTaskTab = vi.fn()

    const Probe = defineComponent({
        setup() {
            const topologyClick = inject(TOPOLOGY_CLICK_INJECTION_KEY, ref())
            return {topologyClick}
        },
        render: () => null,
    })

    const Host = defineComponent({
        setup() {
            useTopologyPanels(panels, openAddTaskTab, openEditTaskTab)
            return () => h(Probe)
        },
    })

    const wrapper = mount(Host)
    const probe = wrapper.findComponent(Probe).vm as unknown as {topologyClick: TopologyClickParams | undefined}
    return {probe, openAddTaskTab, openEditTaskTab}
}

describe("useTopologyPanels addErrorHandler", () => {
    it("opens a create tab under the task errors field when none exists yet", async () => {
        FLOW_YAML = FLOW_WITHOUT_ERRORS
        const {probe, openAddTaskTab} = mountTopologyPanels()

        probe.topologyClick = {action: "addErrorHandler", params: {section: "tasks", id: "first"}}
        await nextTick()

        expect(openAddTaskTab).toHaveBeenCalledWith(
            {panelIndex: -1, tabIndex: -1},
            "tasks[0].errors",
            "#/definitions/io.kestra.core.models.flows.Flow/properties/errors/items",
            -1,
            "after",
            undefined,
            1,
        )
    })

    it("appends after the last handler when the task already has errors", async () => {
        FLOW_YAML = FLOW_WITH_ERRORS
        const {probe, openAddTaskTab} = mountTopologyPanels()

        probe.topologyClick = {action: "addErrorHandler", params: {section: "tasks", id: "first"}}
        await nextTick()

        expect(openAddTaskTab).toHaveBeenCalledWith(
            {panelIndex: -1, tabIndex: -1},
            "tasks[0].errors",
            "#/definitions/io.kestra.core.models.flows.Flow/properties/errors/items",
            1,
            "after",
            undefined,
            1,
        )
    })
})
