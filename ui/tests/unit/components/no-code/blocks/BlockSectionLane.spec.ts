import {describe, test, expect, beforeEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {computed, defineComponent, ref} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"
import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"
import BlockSectionLane from "../../../../../src/components/no-code/blocks/BlockSectionLane.vue"
import {useBlockDragAndDrop} from "../../../../../src/components/no-code/blocks/useBlockDragAndDrop"
import {BLOCK_DRAG_INJECTION_KEY} from "../../../../../src/components/no-code/injectionKeys"
import {i18nMount} from "../../../i18nMount"

const FLOW_WITH_IF_AND_LEAF = `
id: my_flow
namespace: company.team
tasks:
  - id: if_task
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: nested_a
        type: io.kestra.plugin.core.log.Log
  - id: leaf_task
    type: io.kestra.plugin.core.log.Log
`.trim()

describe("BlockSectionLane drag and drop", () => {
    beforeEach(() => setActivePinia(createPinia()))

    test("a real drag/drop sequence drags a top-level FlowableClusterCard by its header and drops it on the lane's trailing drop zone", async () => {
        // Given — a top-level "tasks" lane holding a Flowable and a leaf task, rendered for real
        // (no stubs), sharing the BlockDragContext BlockEditor provides
        const flowYaml = ref(FLOW_WITH_IF_AND_LEAF)
        const applyYaml = (yaml: string) => {
            flowYaml.value = yaml
        }
        const dragContext = useBlockDragAndDrop(flowYaml, applyYaml, () => undefined)

        const Host = defineComponent({
            components: {BlockSectionLane},
            setup() {
                const tasks = computed(() => flowYamlUtils.parse<{tasks: Record<string, unknown>[]}>(flowYaml.value)!.tasks)
                return {tasks, icon: {template: "<span />"}}
            },
            template: `
                <BlockSectionLane
                    section="tasks"
                    title="Tasks"
                    :icon="icon"
                    addLabel="Add task"
                    emptyLabel="task"
                    endDropTest="tasks-end-drop"
                    :playgroundEnabled="false"
                    :supportsFlowable="true"
                    :blocks="tasks"
                />
            `,
        })

        const wrapper = i18nMount(Host, {
            global: {
                plugins: [KestraDesignSystem],
                provide: {[BLOCK_DRAG_INJECTION_KEY as unknown as string]: dragContext},
            },
        })

        const header = wrapper.find("[data-test='flowable-cluster-header']")
        const endDrop = wrapper.find("[data-test='tasks-end-drop']")
        expect(header.exists()).toBe(true)
        expect(header.attributes("draggable")).toBe("true")
        expect(endDrop.exists()).toBe(true)

        // When — the FlowableClusterCard header starts a drag and is dropped on the top-level
        // lane's trailing drop zone, moving it after the leaf task
        await header.trigger("dragstart", {dataTransfer: {}})
        await endDrop.trigger("dragover", {dataTransfer: {}})
        await endDrop.trigger("drop", {dataTransfer: {}})
        await wrapper.vm.$nextTick()

        // Then — the Flowable moved to the end of the top-level "tasks" list
        const parsed = flowYamlUtils.parse<{tasks: {id: string}[]}>(flowYaml.value)!
        expect(parsed.tasks.map((task) => task.id)).toEqual(["leaf_task", "if_task"])
    })
})
