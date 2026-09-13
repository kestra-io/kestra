import {describe, test, expect, beforeEach} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import BranchLane from "../../../../../src/components/no-code/blocks/BranchLane.vue"

const globalConfig = {
    plugins: [
        createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false}),
        KestraDesignSystem,
    ],
    stubs: {
        FlowableClusterCard: {
            name: "FlowableClusterCard",
            props: ["block", "path"],
            emits: ["update-depends-on", "reorder", "add-at-path"],
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
        const wrapper = mount(BranchLane, {
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
        const wrapper = mount(BranchLane, {
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
