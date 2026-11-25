import {beforeEach, describe, expect, test} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import FlowableClusterCard from "./FlowableClusterCard.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const globalConfig = {
    plugins: [KestraDesignSystem],
    stubs: {
        BranchLane: {
            name: "BranchLane",
            props: ["laneName", "tasks", "parentPath"],
            template: "<div class='branch-lane-stub' :data-lane='laneName'><span v-for='task in tasks' :key='task.id' data-test='branch-child'>{{ task.id }}</span></div>",
        },
        TaskIcon: {name: "TaskIcon", props: ["cls", "icons", "loadIcon", "onlyIcon"], template: "<span class='task-icon-stub' />"},
    },
}

const FLOWABLE_TYPES = ["Loop", "LoopUntil", "AllowFailure", "WorkingDirectory"] as const
const branchTasks = {
    tasks: [{id: "main_task", type: "io.kestra.plugin.core.log.Log"}],
    errors: [{id: "error_task", type: "io.kestra.plugin.core.log.Log"}],
    finally: [{id: "finally_task", type: "io.kestra.plugin.core.log.Log"}],
}

const sequentialBlock = {
    id: "seq",
    type: "io.kestra.plugin.core.flow.Sequential",
    tasks: [{id: "leaf", type: "io.kestra.plugin.core.log.Log", message: "hi"}],
}

const switchBlock = {
    id: "router",
    type: "io.kestra.plugin.core.flow.Switch",
    value: "{{ inputs.mode }}",
    cases: {
        fast: [{id: "case_fast", type: "io.kestra.plugin.core.log.Log", message: "fast"}],
    },
}

function mountAt(depth: number) {
    return i18nMount(FlowableClusterCard, {
        global: globalConfig,
        props: {block: sequentialBlock, path: "tasks[0]", depth},
    })
}

function mountSwitch() {
    return i18nMount(FlowableClusterCard, {
        global: globalConfig,
        props: {block: switchBlock, path: "tasks[0]", depth: 0},
    })
}

function mountFlowable(type: string, branches: Record<string, unknown>) {
    return i18nMount(FlowableClusterCard, {
        global: globalConfig,
        props: {
            block: {id: "container", type: `io.kestra.plugin.core.flow.${type}`, ...branches},
            path: "tasks[0]",
            depth: 0,
        },
    })
}

async function typeCaseKey(wrapper: ReturnType<typeof mountSwitch>, key: string) {
    await wrapper.find("[data-test='flowable-add-case-input']").setValue(key)
}

describe("FlowableClusterCard", () => {
    beforeEach(() => setActivePinia(createPinia()))

    test("expands its lanes by default at the top level", () => {
        const wrapper = mountAt(0)
        expect(wrapper.find(".flowable-cluster-body").exists()).toBe(true)
        expect(wrapper.find(".branch-lane-stub").exists()).toBe(true)
    })

    test("still expands deeply nested clusters so their tasks stay visible", () => {
        const wrapper = mountAt(5)
        expect(wrapper.find(".flowable-cluster-body").exists()).toBe(true)
        expect(wrapper.find(".branch-lane-stub").exists()).toBe(true)
    })

    test.each(FLOWABLE_TYPES)("renders tasks, errors and finally for %s", (type) => {
        const wrapper = mountFlowable(type, branchTasks)
        const lanes = wrapper.findAllComponents({name: "BranchLane"})

        expect(lanes.map(lane => lane.props("laneName"))).toEqual(["tasks", "errors", "finally"])
        expect(lanes.map(lane => lane.props("tasks"))).toEqual([
            branchTasks.tasks,
            branchTasks.errors,
            branchTasks.finally,
        ])
        expect(lanes.map(lane => lane.props("parentPath"))).toEqual([
            "tasks[0].tasks",
            "tasks[0].errors",
            "tasks[0].finally",
        ])
        expect(wrapper.findAll("[data-test='branch-child']").map(child => child.text())).toEqual([
            "main_task",
            "error_task",
            "finally_task",
        ])
    })

    test.each(FLOWABLE_TYPES)("handles absent errors and finally branches for %s", (type) => {
        const wrapper = mountFlowable(type, {tasks: branchTasks.tasks})
        const lanes = wrapper.findAllComponents({name: "BranchLane"})

        expect(lanes.map(lane => lane.props("laneName"))).toEqual(["tasks", "errors", "finally"])
        expect(lanes.map(lane => lane.props("tasks"))).toEqual([branchTasks.tasks, [], []])
        expect(wrapper.findAll("[data-test='branch-child']").map(child => child.text())).toEqual(["main_task"])
    })

    test("passes a lazy loadIcon to the task icon so real icons resolve", () => {
        const wrapper = mountAt(0)
        const icon = wrapper.findComponent({name: "TaskIcon"})
        expect(icon.exists()).toBe(true)
        expect(typeof icon.props("loadIcon")).toBe("function")
    })

    test("the header toggle collapses the lanes", async () => {
        const wrapper = mountAt(0)
        await wrapper.find(".flowable-cluster-header").trigger("click")
        expect(wrapper.find(".flowable-cluster-body").exists()).toBe(false)
    })

    test("adds a switch case for a key that is free", async () => {
        const wrapper = mountSwitch()
        await typeCaseKey(wrapper, "slow")

        expect(wrapper.find("[data-test='flowable-add-case-btn']").attributes("disabled")).toBeUndefined()
        await wrapper.find("[data-test='flowable-add-case-btn']").trigger("click")

        expect(wrapper.emitted("add-at-path")?.[0]?.[0]).toBe("tasks[0].cases.slow")
    })

    test("refuses a case key the switch already uses instead of retargeting that case", async () => {
        const wrapper = mountSwitch()
        await typeCaseKey(wrapper, "fast")

        expect(wrapper.find("[data-test='flowable-add-case-btn']").attributes("disabled")).toBeDefined()
        expect(wrapper.find("[data-test='flowable-add-case-taken']").exists()).toBe(true)
    })

    test("frees the button again once the duplicate key is edited away", async () => {
        const wrapper = mountSwitch()
        await typeCaseKey(wrapper, "fast")
        await typeCaseKey(wrapper, "fast_2")

        expect(wrapper.find("[data-test='flowable-add-case-btn']").attributes("disabled")).toBeUndefined()
        expect(wrapper.find("[data-test='flowable-add-case-taken']").exists()).toBe(false)
    })
})
