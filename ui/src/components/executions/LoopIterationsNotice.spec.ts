import {beforeEach, describe, expect, it, vi} from "vitest"
import {reactive} from "vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

type ExecutionsStoreStub = {
    flow: unknown;
    execution?: {
        id: string;
        kind?: string;
        loopRun?: {taskId: string; taskRunId: string};
        taskRunList: {id: string; taskId: string; parentTaskRunId?: string}[];
    };
}

const store = vi.hoisted(() => ({
    executions: {} as ExecutionsStoreStub,
}))

vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => store.executions,
}))

import LoopIterationsNotice from "./LoopIterationsNotice.vue"

const flow = {
    id: "loop",
    namespace: "company.team",
    tasks: [
        {id: "loop", type: "io.kestra.plugin.core.flow.Loop", tasks: [{id: "hello", type: "io.kestra.plugin.core.log.Log"}]},
        {id: "out", type: "io.kestra.plugin.core.log.Log"},
    ],
}

function mountNotice(taskId?: string) {
    return i18nMount(LoopIterationsNotice, {
        props: {kind: "logs", taskId},
        messages: {loop_iterations_notice: {logs: "Loop iterations logs live in their sub-executions", view_iterations: "View iterations"}},
    })
}

function iterationsQuery(wrapper: ReturnType<typeof mountNotice>) {
    return wrapper.findComponent({name: "RouterLink"}).props("to").query
}

describe("LoopIterationsNotice", () => {
    beforeEach(() => {
        store.executions = reactive<ExecutionsStoreStub>({flow, execution: undefined})
    })

    it("should show the notice when the execution ran a Loop task", () => {
        store.executions.execution = {id: "exec-1", taskRunList: [{id: "tr-1", taskId: "loop"}, {id: "tr-2", taskId: "out"}]}

        const wrapper = mountNotice()

        expect(wrapper.get("[data-test=loop-iterations-notice]").text()).toContain("Loop iterations logs live in their sub-executions")
        expect(iterationsQuery(wrapper)).toEqual({"filters[parentId][EQUALS]": "exec-1", "filters[kind][EQUALS]": "LOOP"})
    })

    it("should narrow the iterations link to the given Loop task", () => {
        store.executions.execution = {id: "exec-1", taskRunList: [{id: "tr-1", taskId: "loop"}]}

        expect(iterationsQuery(mountNotice("loop"))).toEqual({
            "filters[parentId][EQUALS]": "exec-1",
            "filters[kind][EQUALS]": "LOOP",
            "filters[taskId][EQUALS]": "loop",
        })
    })

    it("should hide the notice on an iteration sub-execution, whose task runs are the loop children", () => {
        store.executions.execution = {
            id: "exec-2",
            kind: "LOOP",
            loopRun: {taskId: "loop", taskRunId: "tr-1"},
            taskRunList: [{id: "tr-3", taskId: "hello", parentTaskRunId: "tr-1"}],
        }

        expect(mountNotice().find("[data-test=loop-iterations-notice]").exists()).toBe(false)
    })
})
