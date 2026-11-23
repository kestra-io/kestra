import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, h, ref} from "vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {useExecutionsStore} from "../../../stores/executions"
import GanttIterationStepper from "./GanttIterationStepper.vue"
import {GANTT_TREE, createGanttTree, createIterationNode, type GanttTree, type LoopRef} from "./ganttTree"

vi.mock("vue-router", async (importOriginal) => ({
    ...await importOriginal<typeof import("vue-router")>(),
    useRoute: () => ({params: {}}),
    RouterLink: defineComponent({setup: (_, {slots}) => () => h("a", slots.default?.())}),
}))

const histories = [
    {state: "RUNNING", date: "2026-01-01T00:00:00Z"},
    {state: "SUCCESS", date: "2026-01-01T00:00:01Z"},
]
const loopRef: LoopRef = {parentExecutionId: "root", taskId: "per_region", namespace: "company.team", flowId: "monthly_close"}
const TOTAL = 30

const iterationRow = (index: number) => ({id: `it-${index}`, index, value: `REGION-${index}`, state: {current: "SUCCESS", histories}})

let tree: GanttTree

beforeEach(async () => {
    setActivePinia(createPinia())
    vi.spyOn(useExecutionsStore(), "findExecutions").mockImplementation((async (options: Record<string, unknown>) => {
        if (options.onlyTotal) return options["filters[state][IN]"] ? 0 : TOTAL
        const size = (options.size as number) ?? 10
        const start = ((options.page as number) - 1) * size
        const results = Array.from({length: Math.max(0, Math.min(size, TOTAL - start))}, (_, i) => {
            const row = iterationRow(start + i)
            return {id: row.id, state: row.state, loopRun: {index: row.index, value: row.value}}
        })
        return {results, total: TOTAL}
    }) as never)
    tree = createGanttTree({compact: ref(false), levelFilter: ref(undefined)})
    await tree.loopHandle(loopRef).ensureLoaded()
})

function mountStepperOn(index: number) {
    const node = createIterationNode(loopRef, iterationRow(index), [{key: "tr:tr-loop", label: "per_region"}])
    tree.select(node)
    return i18nMount(GanttIterationStepper, {
        props: {node},
        global: {provide: {[GANTT_TREE as symbol]: tree}},
    })
}

describe("GanttIterationStepper", () => {
    it("shouldNameTheLoadedNeighbourWhenTheIterationBeforeWasDeleted", async () => {
        const indexes = Array.from({length: TOTAL}, (_, index) => index).filter(index => index !== 3)
        vi.spyOn(useExecutionsStore(), "findExecutions").mockImplementation((async (options: Record<string, unknown>) => {
            if (options.onlyTotal) return options["filters[state][IN]"] ? 0 : indexes.length
            const size = options.size as number
            const start = ((options.page as number) - 1) * size
            const results = indexes.slice(start, start + size).map(index => {
                const row = iterationRow(index)
                return {id: row.id, state: row.state, loopRun: {index: row.index, value: row.value}}
            })
            return {results, total: indexes.length}
        }) as never)
        tree = createGanttTree({compact: ref(false), levelFilter: ref(undefined)})
        await tree.loopHandle(loopRef).ensureLoaded()

        const wrapper = mountStepperOn(4)

        expect(wrapper.get("[data-test='gantt-step-previous']").text()).toContain("#3 REGION-2")
    })

    it("shouldNotNameAnIterationItWillNotOpenWhenThePreviousOneIsNotLoaded", async () => {
        const wrapper = mountStepperOn(11)
        const previous = wrapper.get("[data-test='gantt-step-previous']")
        expect(previous.text()).not.toContain("#10")

        await previous.trigger("click")
        await flushPromises()

        expect(tree.selected.value?.key).toBe("it:it-10")
        expect(previous.text()).toBe("previous")
    })
})
