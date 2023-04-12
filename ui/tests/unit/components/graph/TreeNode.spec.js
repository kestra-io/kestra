import {describe, it, expect} from "vitest"
import _ from "lodash";
import TreeTaskNode from "../../../../src/components/graph/TreeTaskNode.vue";
import EACH_SEQUENTIAL_FLOWGRAPH from "../../../fixtures/flowgraphs/each-sequential.json";
import EACH_SEQUENTIAL_EXECUTION from "../../../fixtures/executions/each-sequential.json";
import mount from "../../../local.js";

const localMount = (n, execution) => {
    return mount(
        TreeTaskNode,
        {
            props: {
                n: n,
                execution: execution,
                flowId: "flowId",
                namespace: "namespace",
                isFlowable: false,
                isReadOnly: false,
                isAllowedEdit: false
            }
        },
        (store) => {
            store.commit("execution/setExecution", execution)
        }
    )
}

describe("TreeTaskNode", () => {
    it("success execution", () => {
        const wrapper = localMount(
            EACH_SEQUENTIAL_FLOWGRAPH.nodes.filter(r => r.uid === "1-2")[0],
            EACH_SEQUENTIAL_EXECUTION,
        )

        expect(wrapper.vm.task.id).toBe("1-2");
        expect(wrapper.vm.state).toBe("SUCCESS");
        expect(wrapper.vm.taskRuns).toHaveLength(3);
        expect(wrapper.vm.histories[0].state).toBe("CREATED");
        expect(wrapper.vm.histories[0].date.toISOString()).toBe("2020-12-26T20:38:16.001Z");
        expect(wrapper.vm.histories[1].state).toBe("SUCCESS");
        expect(wrapper.vm.histories[1].date.toISOString()).toBe("2020-12-26T20:38:16.002Z");
    })

    it("sorting state", () => {
        const taskRun = EACH_SEQUENTIAL_EXECUTION.taskRunList.filter(r => r.id === "68GkiSkymFFcFXsopKydNF")[0];
        const taskRunIndex = EACH_SEQUENTIAL_EXECUTION.taskRunList.indexOf(taskRun);
        const failed = _.clone(EACH_SEQUENTIAL_EXECUTION)

        failed.taskRunList[taskRunIndex] = _.merge({taskId: "1-2", state: {current: "FAILED"}})

        const wrapper = localMount(
            EACH_SEQUENTIAL_FLOWGRAPH.nodes.filter(r => r.uid === "1-2")[0],
            failed,
        )

        expect(wrapper.vm.state).toBe("FAILED");
    })
})
