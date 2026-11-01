import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {ref} from "vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {useExecutionsStore} from "../../../stores/executions"
import GanttLoopChips from "./GanttLoopChips.vue"
import {GANTT_TREE, createGanttTree, type GanttTree, type LoopRef} from "./ganttTree"

const loopA: LoopRef = {parentExecutionId: "root", taskId: "per_region", namespace: "company.team", flowId: "monthly_close"}
const loopB: LoopRef = {parentExecutionId: "root", taskId: "per_quarter", namespace: "company.team", flowId: "monthly_close"}

const totals: Record<string, {all: number; failed: number}> = {
    per_region: {all: 12, failed: 3},
    per_quarter: {all: 4, failed: 2},
}

let tree: GanttTree

beforeEach(() => {
    setActivePinia(createPinia())
    vi.spyOn(useExecutionsStore(), "findExecutions").mockImplementation((async (options: Record<string, unknown>) => {
        const total = totals[options["filters[taskId][EQUALS]"] as string]
        return options["filters[state][IN]"] ? total.failed : total.all
    }) as never)
    tree = createGanttTree({compact: ref(false), levelFilter: ref(undefined)})
})

function mountChips(loop: LoopRef) {
    return i18nMount(GanttLoopChips, {
        props: {loop, state: "SUCCESS", nodeKey: `tr:${loop.taskId}`},
        messages: {gantt_loop_failed_count: "{count} failed"},
        global: {provide: {[GANTT_TREE as symbol]: tree}},
    })
}

describe("GanttLoopChips", () => {
    it("shouldFollowTheNewLoopWhenARecycledRowReceivesAnotherLoop", async () => {
        const wrapper = mountChips(loopA)
        await flushPromises()
        expect(wrapper.get("[data-test='gantt-loop-failed']").text()).toBe("3 failed")

        await wrapper.setProps({loop: loopB, nodeKey: "tr:per_quarter"})
        await flushPromises()
        await wrapper.get("[data-test='gantt-loop-failed']").trigger("click")

        expect(wrapper.get("[data-test='gantt-loop-failed']").text()).toBe("2 failed")
        expect(tree.loopHandle(loopB).failedOnly.value).toBe(true)
        expect(tree.loopHandle(loopA).failedOnly.value).toBe(false)
    })
})
