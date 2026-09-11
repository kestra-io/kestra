import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent, reactive} from "vue"

const store = vi.hoisted(() => ({
    executions: {} as Record<string, any>,
}))

vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => store.executions,
}))

import LoopIterationsNotice from "./LoopIterationsNotice.vue"

const i18n = createI18n({
    legacy: false,
    globalInjection: true,
    locale: "en",
    messages: {en: {loop_iterations_notice: {logs: "Loop iterations logs live in their sub-executions", view_iterations: "View iterations"}}},
})

const KsAlertStub = defineComponent({
    name: "KsAlert",
    template: "<div data-test=\"notice\"><slot /></div>",
})

const KsButtonStub = defineComponent({
    name: "KsButton",
    template: "<button><slot /></button>",
})

const flow = {
    id: "loop",
    namespace: "company.team",
    tasks: [
        {id: "loop", type: "io.kestra.plugin.core.flow.Loop", tasks: [{id: "hello", type: "io.kestra.plugin.core.log.Log"}]},
        {id: "out", type: "io.kestra.plugin.core.log.Log"},
    ],
}

function mountNotice() {
    return mount(LoopIterationsNotice, {
        props: {kind: "logs"},
        global: {plugins: [i18n], stubs: {KsAlert: KsAlertStub, KsButton: KsButtonStub}},
    })
}

describe("LoopIterationsNotice", () => {
    beforeEach(() => {
        store.executions = reactive({flow, execution: undefined as any})
    })

    it("should show the notice when the execution ran a Loop task", () => {
        store.executions.execution = {id: "exec-1", taskRunList: [{id: "tr-1", taskId: "loop"}, {id: "tr-2", taskId: "out"}]}

        expect(mountNotice().find("[data-test=notice]").exists()).toBe(true)
    })

    it("should hide the notice on an iteration sub-execution, whose task runs are the loop children", () => {
        store.executions.execution = {
            id: "exec-2",
            kind: "LOOP",
            loopRun: {taskId: "loop", taskRunId: "tr-1"},
            taskRunList: [{id: "tr-3", taskId: "hello", parentTaskRunId: "tr-1"}],
        }

        expect(mountNotice().find("[data-test=notice]").exists()).toBe(false)
    })
})
