import _ from "lodash";
import TreeNode from "../../../../src/components/graph/TreeNode.vue";
import EACH_SEQUENTIAL_FLOWGRAPH from "../../../fixtures/flowgraphs/each-sequential.json";
import EACH_SEQUENTIAL_EXECUTION from "../../../fixtures/executions/each-sequential.json";
import mount from "../../../local.js";

const localMount = (n, execution) => {
    return mount(TreeNode, {
        props: {
            n: n,
            execution: execution,
            flowId: "flowId",
            namespace: "namespace",
        }
    })
}

describe("TreeNode", () => {
    it("success execution", () => {
        const wrapper = localMount(
            EACH_SEQUENTIAL_FLOWGRAPH.nodes.filter(r => r.uid === "1-2")[0],
            EACH_SEQUENTIAL_EXECUTION,
        )

        expect(wrapper.vm.task.id).toBe("1-2");
        expect(wrapper.vm.state).toBe("SUCCESS");
        expect(wrapper.vm.taskRuns).toHaveLength(3);
        expect(wrapper.vm.duration).toBe(0.000633852);
    })

    it("sorting state", () => {
        const taskRun = EACH_SEQUENTIAL_EXECUTION.taskRunList.filter(r => r.id === "68GkiSkymFFcFXsopKydNF")[0];
        const taskRunIndex = EACH_SEQUENTIAL_EXECUTION.taskRunList.indexOf(taskRun);
        const failed = _.clone(EACH_SEQUENTIAL_EXECUTION)

        failed.taskRunList[taskRunIndex] = _.merge({taskId:"1-2", state: {current: "FAILED"}})

        const wrapper = localMount(
            EACH_SEQUENTIAL_FLOWGRAPH.nodes.filter(r => r.uid === "1-2")[0],
            failed,
        )

        expect(wrapper.vm.state).toBe("FAILED");
    })
})
