import {describe, expect, it} from "vitest"
import TaskRunIterationSelector from "./TaskRunIterationSelector.vue"
import type {ExecutionTaskRun} from "./executionTaskRun"
import {i18nMount} from "../../../tests/unit/i18nMount"

const run = (id: string, value?: string) => ({id, taskId: "loop", value, state: {current: "SUCCESS", histories: []}}) as unknown as ExecutionTaskRun

function mountSelector(taskRuns: ExecutionTaskRun[], selectedId?: string) {
    return i18nMount(TaskRunIterationSelector, {
        messages: {iteration_number: "Iteration {number}", search: "Search"},
        props: {taskRuns, selectedId},
        global: {stubs: {KsDropdownItem: {template: "<div><slot /></div>"}, KsSearch: {props: ["modelValue"], template: "<input data-test='search' :value='modelValue' @input=\"$emit('update:modelValue', $event.target.value)\" />"}}},
    })
}

describe("TaskRunIterationSelector", () => {
    it("shouldLabelEachRunByItsValueOrItsPosition", () => {
        const wrapper = mountSelector([run("a", "eu-west"), run("b")])

        expect(wrapper.findAll("[data-test='task-run-iteration']").map((item) => item.text())).toEqual(["eu-west", "Iteration 2"])
    })

    it("shouldEmitTheIdOfTheClickedRun", async () => {
        const wrapper = mountSelector([run("a"), run("b")])

        await wrapper.findAll("[data-test='task-run-iteration']")[1].trigger("click")

        expect(wrapper.emitted("select")).toEqual([["b"]])
    })

    it("shouldKeepTheOriginalPositionOfARunOnceTheListIsFiltered", async () => {
        const wrapper = mountSelector([run("a"), run("b"), run("c")])

        await wrapper.get("[data-test='search']").setValue("3")

        expect(wrapper.findAll("[data-test='task-run-iteration']").map((item) => item.text())).toEqual(["Iteration 3"])
    })
})
