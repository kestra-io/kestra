import {beforeEach, describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import FlowableClusterCard from "../../../../../src/components/no-code/blocks/FlowableClusterCard.vue"

const globalConfig = {
    plugins: [
        createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false}),
        KestraDesignSystem,
    ],
    stubs: {
        BranchLane: {name: "BranchLane", props: ["laneName", "tasks"], template: "<div class='branch-lane-stub' :data-lane='laneName' />"},
        TaskIcon: {name: "TaskIcon", props: ["cls", "icons", "loadIcon", "onlyIcon"], template: "<span class='task-icon-stub' />"},
    },
}

const sequentialBlock = {
    id: "seq",
    type: "io.kestra.plugin.core.flow.Sequential",
    tasks: [{id: "leaf", type: "io.kestra.plugin.core.log.Log", message: "hi"}],
}

function mountAt(depth: number) {
    return mount(FlowableClusterCard, {
        global: globalConfig,
        props: {block: sequentialBlock, path: "tasks[0]", depth},
    })
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
})
