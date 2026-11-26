import {describe, it, expect, vi} from "vitest"
import {defineComponent, h} from "vue"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

const flowStore = vi.hoisted(() => ({value: {flow: {namespace: "company.team", id: "new_flow"}} as {flow: {namespace: string, id: string}}}))
vi.mock("../../stores/flow", async () => {
    const {reactive} = await import("vue")
    flowStore.value = reactive(flowStore.value)
    return {useFlowStore: () => flowStore.value}
})

const totals: Record<string, number> = {"company.team/new_flow": 0, "company.team/all_inputs": 4}
vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({
        findExecutions: (query: Record<string, string>) => Promise.resolve({total: totals[`${query["filters[namespace][EQUALS]"]}/${query["filters[flowId][EQUALS]"]}`]}),
    }),
}))

vi.mock("../dashboard/Dashboard.vue", () => ({
    default: defineComponent({name: "Dashboard", setup: () => () => h("div", {"data-test": "dashboard"})}),
}))
vi.mock("./NoExecutions.vue", () => ({
    default: defineComponent({name: "NoExecutions", setup: () => () => h("div", {"data-test": "no-executions"})}),
}))

import Overview from "./Overview.vue"

describe("Overview", () => {
    it("counts the executions of the flow it switches to", async () => {
        const wrapper = i18nMount(Overview)
        await flushPromises()
        expect(wrapper.find("[data-test=no-executions]").exists()).toBe(true)

        flowStore.value.flow = {namespace: "company.team", id: "all_inputs"}
        await flushPromises()
        expect(wrapper.find("[data-test=dashboard]").exists()).toBe(true)
        expect(wrapper.find("[data-test=no-executions]").exists()).toBe(false)

        flowStore.value.flow = {namespace: "company.team", id: "new_flow"}
        await flushPromises()
        expect(wrapper.find("[data-test=no-executions]").exists()).toBe(true)
        expect(wrapper.find("[data-test=dashboard]").exists()).toBe(false)
    })
})
