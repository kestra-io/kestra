import {describe, test, expect, beforeEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {computed, defineComponent, ref} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"
import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"
import BranchLane from "../../../../../src/components/no-code/blocks/BranchLane.vue"
import {useBlockDragAndDrop} from "../../../../../src/components/no-code/blocks/useBlockDragAndDrop"
import {BLOCK_DRAG_INJECTION_KEY} from "../../../../../src/components/no-code/injectionKeys"
import {i18nMount} from "../../../i18nMount"

const globalConfig = {
    plugins: [KestraDesignSystem],
    stubs: {
        FlowableClusterCard: {
            name: "FlowableClusterCard",
            props: ["block", "path"],
            emits: ["update-depends-on", "add-at-path"],
            template: "<div class='cluster-stub' />",
        },
        LeafBlockCard: {name: "LeafBlockCard", props: ["block", "path"], template: "<div class='leaf-stub' />"},
        DagDependsOnEditor: {
            name: "DagDependsOnEditor",
            props: ["dependsOn", "siblingIds"],
            emits: ["update"],
            template: "<div class='depends-stub' />",
        },
    },
}

describe("BranchLane", () => {
    beforeEach(() => setActivePinia(createPinia()))

    test("forwards update-depends-on emitted by a nested flowable cluster", async () => {
        const wrapper = i18nMount(BranchLane, {
            global: globalConfig,
            props: {
                laneName: "tasks",
                parentPath: "tasks[0].tasks",
                tasks: [{id: "nested_dag", type: "io.kestra.plugin.core.flow.Dag"}],
            },
        })

        const cluster = wrapper.findComponent({name: "FlowableClusterCard"})
        expect(cluster.exists()).toBe(true)

        cluster.vm.$emit("update-depends-on", "tasks[0].tasks[0].tasks[1]", ["dag_a"])
        await wrapper.vm.$nextTick()

        const emitted = wrapper.emitted("update-depends-on")
        expect(emitted).toBeTruthy()
        expect(emitted![0]).toEqual(["tasks[0].tasks[0].tasks[1]", ["dag_a"]])
    })

    test("forwards update-depends-on from a direct DAG dependsOn editor", async () => {
        const wrapper = i18nMount(BranchLane, {
            global: globalConfig,
            props: {
                laneName: "tasks",
                parentPath: "my_dag.tasks",
                tasks: [{task: {id: "dag_b", type: "io.kestra.plugin.core.log.Log"}, dependsOn: []}],
            },
        })

        const editor = wrapper.findComponent({name: "DagDependsOnEditor"})
        expect(editor.exists()).toBe(true)

        editor.vm.$emit("update", ["dag_a"])
        await wrapper.vm.$nextTick()

        const emitted = wrapper.emitted("update-depends-on")
        expect(emitted).toBeTruthy()
        expect(emitted![0]).toEqual(["my_dag.tasks[0]", ["dag_a"]])
    })
})

const FLOW_WITH_IF_AND_SEQUENTIAL = `
id: my_flow
namespace: company.team
tasks:
  - id: if_task
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: nested_a
        type: io.kestra.plugin.core.log.Log
  - id: seq_task
    type: io.kestra.plugin.core.flow.Sequential
    tasks:
      - id: seq_a
        type: io.kestra.plugin.core.log.Log
`.trim()

describe("BranchLane drag across lanes", () => {
    beforeEach(() => setActivePinia(createPinia()))

    test("a real drag/drop sequence moves a task from one mounted BranchLane into another, pruning the emptied lane", async () => {
        // Given — two independently-mounted BranchLanes sharing one BlockDragContext, as BlockEditor wires them
        const flowYaml = ref(FLOW_WITH_IF_AND_SEQUENTIAL)
        const applyYaml = (yaml: string) => {
            flowYaml.value = yaml
        }
        const dragContext = useBlockDragAndDrop(flowYaml, applyYaml, () => undefined)

        const Host = defineComponent({
            components: {BranchLane},
            setup() {
                const thenTasks = computed(() => flowYamlUtils.parse(flowYaml.value).tasks[0].then ?? [])
                const seqTasks = computed(() => flowYamlUtils.parse(flowYaml.value).tasks[1].tasks ?? [])
                return {thenTasks, seqTasks}
            },
            template: `
                <div>
                    <BranchLane laneName="then" parentPath="tasks[0].then" :tasks="thenTasks" />
                    <BranchLane laneName="tasks" parentPath="tasks[1].tasks" :tasks="seqTasks" />
                </div>
            `,
        })

        const wrapper = i18nMount(Host, {
            global: {
                plugins: [KestraDesignSystem],
                provide: {[BLOCK_DRAG_INJECTION_KEY as unknown as string]: dragContext},
            },
        })

        const cards = wrapper.findAll("[data-test='nested-block-card']")
        expect(cards).toHaveLength(2)

        // When — dragging the If's only "then" task onto the Sequential's task
        await cards[0].trigger("dragstart", {dataTransfer: {}})
        await cards[1].trigger("dragover", {dataTransfer: {}})
        await cards[1].trigger("drop", {dataTransfer: {}})
        await wrapper.vm.$nextTick()

        // Then — the "then" lane is pruned once empty, and the task landed in the Sequential
        const parsed = flowYamlUtils.parse(flowYaml.value)
        expect(parsed.tasks[0].then).toBeUndefined()
        expect(parsed.tasks[1].tasks.map((task: {id: string}) => task.id)).toEqual(["nested_a", "seq_a"])
    })
})
